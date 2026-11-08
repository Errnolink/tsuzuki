package tsuzuki.host;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import eu.kanade.tachiyomi.source.CatalogueSource;
import eu.kanade.tachiyomi.source.Source;
import eu.kanade.tachiyomi.source.SourceFactory;
import eu.kanade.tachiyomi.source.model.MangasPage;
import eu.kanade.tachiyomi.source.model.Page;
import eu.kanade.tachiyomi.source.model.SChapter;
import eu.kanade.tachiyomi.source.model.SManga;
import eu.kanade.tachiyomi.source.model.SMangaUpdate;
import eu.kanade.tachiyomi.source.online.HttpSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import okhttp3.Response;
import suwayomi.tachidesk.manga.impl.util.PackageTools;

final class Extensions {
    private final Path jars;
    private final Map<Long, CatalogueSource> sources = new ConcurrentHashMap<>();
    private final Map<Long, Gate> gates = new ConcurrentHashMap<>();

    Extensions(String root) {
        jars = Paths.get(root, "jars");
    }

    Object call(String op, JSONObject args) throws Exception {
        return switch (op) {
            case "ping" -> "pong";
            case "load" -> load(Paths.get(args.getString("apk")));
            case "popular" -> page(args, (s, p, c) -> s.getPopularManga(p, c));
            case "latest" -> page(args, (s, p, c) -> s.getLatestUpdates(p, c));
            case "search" -> page(args, (s, p, c) -> s.getSearchManga(p, args.getString("query"), s.getFilterList(), c));
            case "update" -> update(args);
            case "pages" -> pages(args);
            case "image" -> image(args);
            default -> throw new IllegalArgumentException("Unknown op " + op);
        };
    }

    private JSONObject load(Path apk) throws Exception {
        Files.createDirectories(jars);
        var info = PackageTools.INSTANCE.getPackageInfo(apk);
        Path jar = jars.resolve(info.packageName + "-" + info.versionCode + ".jar");
        if (!Files.exists(jar)) {
            DexConverter.convert(apk, jar);
        }
        var meta = info.applicationInfo.metaData;
        String className = meta.getString("tachiyomi.extension.class");
        String full = className.startsWith(".") ? info.packageName + className : className;
        Object loaded = run((scope, c) -> PackageTools.INSTANCE.loadExtensionSources(jar, full, c));
        List<Source> created = new ArrayList<>();
        if (loaded instanceof SourceFactory factory) {
            created.addAll(factory.createSources());
        } else if (loaded instanceof Source single) {
            created.add(single);
        }
        JSONArray list = new JSONArray();
        for (Source source : created) {
            if (!(source instanceof CatalogueSource catalogue)) {
                continue;
            }
            sources.put(catalogue.getId(), catalogue);
            JSONObject entry = new JSONObject()
                .fluentPut("id", String.valueOf(catalogue.getId()))
                .fluentPut("name", catalogue.getName())
                .fluentPut("lang", catalogue.getLang())
                .fluentPut("supportsLatest", catalogue.getSupportsLatest());
            if (catalogue instanceof HttpSource http) {
                entry.put("baseUrl", http.getBaseUrl());
            }
            list.add(entry);
        }
        return new JSONObject()
            .fluentPut("pkg", info.packageName)
            .fluentPut("versionName", info.versionName)
            .fluentPut("versionCode", info.versionCode)
            .fluentPut("signature", PackageTools.INSTANCE.getSignatureHash(info))
            .fluentPut("sources", list);
    }

    private JSONObject page(JSONObject args, PageCall call) throws Exception {
        CatalogueSource source = source(args);
        int page = args.getIntValue("page", 1);
        MangasPage result = gated(source, () -> run((scope, c) -> call.invoke(source, page, c)));
        JSONArray mangas = new JSONArray();
        for (SManga manga : result.getMangas()) {
            mangas.add(manga(manga));
        }
        return new JSONObject().fluentPut("mangas", mangas).fluentPut("hasNext", result.getHasNextPage());
    }

    private JSONObject update(JSONObject args) throws Exception {
        CatalogueSource source = source(args);
        SManga manga = SManga.Companion.create();
        manga.setUrl(args.getString("url"));
        manga.setTitle(args.getString("title") == null ? "" : args.getString("title"));
        String memo = args.getString("memo");
        if (memo != null && !memo.isEmpty()) {
            manga.setMemo(parse(memo));
        }
        boolean details = args.getBooleanValue("details", true);
        boolean chapters = args.getBooleanValue("chapters", true);
        SMangaUpdate result = gated(source, () -> run((scope, c) -> source.getMangaUpdate(manga, List.of(), details, chapters, c)));
        JSONArray list = new JSONArray();
        if (result.getChapters() != null) {
            for (SChapter chapter : result.getChapters()) {
                list.add(chapter(chapter));
            }
        }
        return new JSONObject().fluentPut("manga", manga(result.getManga())).fluentPut("chapters", list);
    }

    private JSONArray pages(JSONObject args) throws Exception {
        CatalogueSource source = source(args);
        SChapter chapter = SChapter.Companion.create();
        chapter.setUrl(args.getString("url"));
        chapter.setName(args.getString("name") == null ? "" : args.getString("name"));
        String memo = args.getString("memo");
        if (memo != null && !memo.isEmpty()) {
            chapter.setMemo(parse(memo));
        }
        List<? extends Page> pages = gated(source, () -> run((scope, c) -> source.getPageList(chapter, c)));
        JSONArray list = new JSONArray();
        for (Page page : pages) {
            String image = page.getImageUrl();
            if ((image == null || image.isEmpty()) && source instanceof HttpSource http) {
                image = gated(source, () -> (String) run((scope, c) -> http.getImageUrl(page, c)));
            }
            list.add(new JSONObject()
                .fluentPut("index", page.getIndex())
                .fluentPut("url", page.getUrl())
                .fluentPut("imageUrl", image));
        }
        return list;
    }

    private JSONObject image(JSONObject args) throws Exception {
        CatalogueSource source = source(args);
        if (!(source instanceof HttpSource http)) {
            throw new UnsupportedOperationException("Source has no HTTP image loader");
        }
        Page page = new Page(args.getIntValue("index"), args.getString("pageUrl") == null ? "" : args.getString("pageUrl"), args.getString("url"), null);
        Path out = Paths.get(args.getString("out"));
        Path temp = out.resolveSibling(out.getFileName() + ".host");
        long bytes;
        try (Response response = gated(source, () -> (Response) run((scope, c) -> http.getImage(page, c)))) {
            if (!response.isSuccessful()) {
                throw new java.io.IOException("HTTP " + response.code() + " for " + args.getString("url"));
            }
            try (InputStream body = response.body().byteStream()) {
                Files.createDirectories(out.getParent());
                bytes = Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING);
        return new JSONObject().fluentPut("bytes", bytes);
    }

    private CatalogueSource source(JSONObject args) {
        long id = Long.parseLong(args.getString("source"));
        CatalogueSource source = sources.get(id);
        if (source == null) {
            throw new IllegalStateException("Source " + id + " is not loaded");
        }
        return source;
    }

    private <T> T gated(CatalogueSource source, Gate.Work<T> work) throws Exception {
        return gates.computeIfAbsent(source.getId(), id -> new Gate()).run(work);
    }

    private static JSONObject manga(SManga manga) {
        JSONObject json = new JSONObject()
            .fluentPut("url", manga.getUrl())
            .fluentPut("title", manga.getTitle())
            .fluentPut("thumbnail", manga.getThumbnail_url())
            .fluentPut("author", manga.getAuthor())
            .fluentPut("artist", manga.getArtist())
            .fluentPut("description", manga.getDescription())
            .fluentPut("genre", manga.getGenre())
            .fluentPut("status", manga.getStatus())
            .fluentPut("initialized", manga.getInitialized());
        JsonObject memo = manga.getMemo();
        if (memo != null && !memo.isEmpty()) {
            json.put("memo", memo.toString());
        }
        return json;
    }

    private static JSONObject chapter(SChapter chapter) {
        JSONObject json = new JSONObject()
            .fluentPut("url", chapter.getUrl())
            .fluentPut("name", chapter.getName())
            .fluentPut("scanlator", chapter.getScanlator())
            .fluentPut("number", chapter.getChapter_number())
            .fluentPut("date", chapter.getDate_upload());
        JsonObject memo = chapter.getMemo();
        if (memo != null && !memo.isEmpty()) {
            json.put("memo", memo.toString());
        }
        return json;
    }

    private static JsonObject parse(String memo) {
        JsonElement element = Json.Default.parseToJsonElement(memo);
        return JsonElementKt.getJsonObject(element);
    }

    @SuppressWarnings("unchecked")
    private static <T> T run(Function2<CoroutineScope, Continuation<? super T>, Object> block) throws InterruptedException {
        return (T) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, block);
    }

    private interface PageCall {
        Object invoke(CatalogueSource source, int page, Continuation<? super MangasPage> continuation);
    }
}
