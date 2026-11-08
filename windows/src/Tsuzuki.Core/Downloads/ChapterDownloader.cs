using System.Globalization;
using System.IO.Compression;
using System.Text;
using System.Xml;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Downloads;

public sealed class LowSpaceException(string root) : IOException($"Less than 1 GB is free on the drive of {root}")
{
}

internal sealed record ChapterJob(
    MangaDetails Manga,
    ChapterRow Chapter,
    ISource Source,
    string RelativePath,
    IReadOnlyList<string> Categories);

internal sealed class ChapterDownloader(IImageCache pages, IImageCache covers, DownloadEnvironment environment)
{
    public const int PageParallelism = 4;
    private const int PageAttempts = 3;

    public async Task<DownloadedChapter> DownloadAsync(ChapterJob job, string root, IProgress<(int Done, int Total)> progress, CancellationToken ct)
    {
        var final = Path.Combine(root, job.RelativePath);
        var mangaDir = Path.GetDirectoryName(final)!;
        var folder = final[..^4];
        var staging = folder + DownloadNames.TempSuffix;
        if (File.Exists(final) || Directory.Exists(folder))
        {
            DeleteDirectory(staging);
            return DownloadScanner.Describe(job.Chapter.OwnerMangaId, job.Chapter.Url, root, File.Exists(final) ? final : folder, NowMs());
        }
        EnsureSpace(root);
        Directory.CreateDirectory(staging);
        var list = await job.Source.GetPageListAsync(job.Chapter.Url, ct: ct).ConfigureAwait(false);
        if (list.Count == 0)
        {
            throw new InvalidOperationException("The source lists no pages for this chapter.");
        }
        var total = list.Count;
        var done = Enumerable.Range(0, total).Count(i => FindPage(staging, i, total) is not null);
        progress.Report((done, total));
        var refreshed = new Lazy<Task<IReadOnlyList<PageRef>>>(() => job.Source.GetPageListAsync(job.Chapter.Url, refresh: true, ct: ct));
        using var failed = CancellationTokenSource.CreateLinkedTokenSource(ct);
        using var slots = new SemaphoreSlim(PageParallelism);
        var tasks = new List<Task>();
        try
        {
            for (var i = 0; i < total; i++)
            {
                if (FindPage(staging, i, total) is not null)
                {
                    continue;
                }
                var index = i;
                await slots.WaitAsync(failed.Token).ConfigureAwait(false);
                tasks.Add(Task.Run(async () =>
                {
                    try
                    {
                        await FetchPageAsync(job.Source, list, refreshed, index, total, staging, root, failed.Token).ConfigureAwait(false);
                        progress.Report((Interlocked.Increment(ref done), total));
                    }
                    catch (Exception e) when (e is not OperationCanceledException)
                    {
                        failed.Cancel();
                        throw;
                    }
                    finally
                    {
                        slots.Release();
                    }
                }, failed.Token));
            }
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
        }
        await Task.WhenAll(tasks).ConfigureAwait(false);
        ct.ThrowIfCancellationRequested();
        var files = Enumerable.Range(0, total).Select(i => FindPage(staging, i, total)).ToList();
        if (files.Any(f => f is null))
        {
            throw new IOException("Some pages are missing after the download.");
        }
        WriteComicInfo(Path.Combine(staging, DownloadNames.ComicInfoFile), job);
        Archive(staging, final, files!);
        DeleteDirectory(staging);
        await WriteCoverAsync(job, mangaDir, ct).ConfigureAwait(false);
        return new DownloadedChapter(job.Chapter.OwnerMangaId, job.Chapter.Url, job.RelativePath, total, new FileInfo(final).Length, NowMs());
    }

    private async Task FetchPageAsync(ISource source, IReadOnlyList<PageRef> list, Lazy<Task<IReadOnlyList<PageRef>>> refreshed,
        int index, int total, string staging, string root, CancellationToken ct)
    {
        var page = list[index];
        for (var attempt = 1; ; attempt++)
        {
            EnsureSpace(root);
            try
            {
                var path = await pages.GetFileAsync(ImageRequest.Page(source.Id, page), ImagePriority.Background, ct).ConfigureAwait(false);
                CopyPage(path, staging, index, total);
                return;
            }
            catch (Exception e) when (attempt < PageAttempts && !ct.IsCancellationRequested && e is HttpRequestException or IOException or TaskCanceledException && e is not LowSpaceException)
            {
                await Task.Delay(environment.PageRetryDelay, ct).ConfigureAwait(false);
                if (attempt == 1)
                {
                    try
                    {
                        var fresh = await refreshed.Value.ConfigureAwait(false);
                        if (fresh.Count == list.Count)
                        {
                            page = fresh[index];
                        }
                    }
                    catch (Exception refresh) when (refresh is HttpRequestException or InvalidOperationException)
                    {
                    }
                }
            }
        }
    }

    private void EnsureSpace(string root)
    {
        if (environment.FreeBytes(root) < environment.MinimumFreeBytes)
        {
            throw new LowSpaceException(root);
        }
    }

    private static void CopyPage(string cached, string staging, int index, int total)
    {
        var name = DownloadNames.PageFile(index, total, "");
        var temp = Path.Combine(staging, name + ".tmp");
        using (var source = new FileStream(cached, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
        using (var target = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None))
        {
            source.CopyTo(target);
            target.Flush(flushToDisk: true);
        }
        File.Move(temp, Path.Combine(staging, name + ExtensionOf(temp)), overwrite: true);
    }

    internal static string? FindPage(string staging, int index, int total)
    {
        var prefix = DownloadNames.PageFile(index, total, ".");
        if (!Directory.Exists(staging))
        {
            return null;
        }
        return Directory.EnumerateFiles(staging, prefix + "*")
            .FirstOrDefault(f => !f.EndsWith(".tmp", StringComparison.OrdinalIgnoreCase));
    }

    private static string ExtensionOf(string path)
    {
        Span<byte> head = stackalloc byte[16];
        int read;
        using (var stream = File.OpenRead(path))
        {
            read = stream.ReadAtLeast(head, head.Length, throwOnEndOfStream: false);
        }
        head = head[..read];
        if (head.StartsWith((ReadOnlySpan<byte>)[0x89, (byte)'P', (byte)'N', (byte)'G'])) return ".png";
        if (head.StartsWith("GIF8"u8)) return ".gif";
        if (head.Length >= 12 && head.StartsWith("RIFF"u8) && head.Slice(8, 4).SequenceEqual("WEBP"u8)) return ".webp";
        if (head.Length >= 12 && head.Slice(4, 4).SequenceEqual("ftyp"u8)) return ".avif";
        if (head.StartsWith((ReadOnlySpan<byte>)[0xFF, 0x0A]) || head.StartsWith((ReadOnlySpan<byte>)[0x00, 0x00, 0x00, 0x0C, (byte)'J', (byte)'X', (byte)'L'])) return ".jxl";
        if (head.StartsWith("BM"u8)) return ".bmp";
        return ".jpg";
    }

    private static void Archive(string staging, string final, IReadOnlyList<string> pageFiles)
    {
        var temp = final + DownloadNames.TempSuffix;
        using (var stream = new FileStream(temp, FileMode.Create, FileAccess.ReadWrite, FileShare.None))
        {
            using (var zip = new ZipArchive(stream, ZipArchiveMode.Create, leaveOpen: true, Encoding.UTF8))
            {
                foreach (var file in pageFiles.Append(Path.Combine(staging, DownloadNames.ComicInfoFile)))
                {
                    var entry = zip.CreateEntry(Path.GetFileName(file), CompressionLevel.NoCompression);
                    entry.LastWriteTime = File.GetLastWriteTime(file);
                    using var input = File.OpenRead(file);
                    using var output = entry.Open();
                    input.CopyTo(output);
                }
            }
            stream.Flush(flushToDisk: true);
        }
        File.Move(temp, final, overwrite: false);
    }

    private async Task WriteCoverAsync(ChapterJob job, string mangaDir, CancellationToken ct)
    {
        var cover = Path.Combine(mangaDir, DownloadNames.CoverFile);
        if (File.Exists(cover) || job.Manga.ThumbnailUrl is not { } thumbnail || !Uri.TryCreate(thumbnail, UriKind.Absolute, out var url))
        {
            return;
        }
        try
        {
            var path = await covers.GetFileAsync(ImageRequest.Cover(job.Source.Id, url), ImagePriority.Background, ct).ConfigureAwait(false);
            var temp = cover + DownloadNames.TempSuffix;
            File.Copy(path, temp, overwrite: true);
            File.Move(temp, cover, overwrite: true);
        }
        catch (Exception e) when (e is HttpRequestException or IOException or UnauthorizedAccessException)
        {
        }
    }

    internal static void WriteComicInfo(string path, ChapterJob job)
    {
        var manga = job.Manga;
        var chapter = job.Chapter;
        var settings = new XmlWriterSettings { Encoding = new UTF8Encoding(false), Indent = true };
        using var writer = XmlWriter.Create(path, settings);
        const string xsd = "http://www.w3.org/2001/XMLSchema";
        writer.WriteStartDocument();
        writer.WriteStartElement("ComicInfo");
        writer.WriteAttributeString("xmlns", "xsd", null, xsd);
        writer.WriteAttributeString("xmlns", "xsi", null, "http://www.w3.org/2001/XMLSchema-instance");
        writer.WriteElementString("Title", chapter.Name);
        writer.WriteElementString("Series", manga.Title);
        if (chapter.ChapterNumber >= 0)
        {
            writer.WriteElementString("Number", chapter.ChapterNumber % 1 == 0
                ? ((long)chapter.ChapterNumber).ToString(CultureInfo.InvariantCulture)
                : chapter.ChapterNumber.ToString(CultureInfo.InvariantCulture));
        }
        if (manga.Description is { } description) writer.WriteElementString("Summary", description);
        if (manga.Author is { } author) writer.WriteElementString("Writer", author);
        if (manga.Artist is { } artist) writer.WriteElementString("Penciller", artist);
        if (chapter.Scanlator is { } scanlator) writer.WriteElementString("Translator", scanlator);
        if (manga.Genres.Count > 0) writer.WriteElementString("Genre", string.Join(", ", manga.Genres));
        writer.WriteElementString("Web", ChapterWebUrl(job));
        if (chapter.DateUpload > 0)
        {
            var date = DateTimeOffset.FromUnixTimeMilliseconds(chapter.DateUpload).ToLocalTime();
            writer.WriteElementString("Year", date.Year.ToString(CultureInfo.InvariantCulture));
            writer.WriteElementString("Month", date.Month.ToString(CultureInfo.InvariantCulture));
            writer.WriteElementString("Day", date.Day.ToString(CultureInfo.InvariantCulture));
        }
        writer.WriteStartElement("ty", "PublishingStatusTachiyomi", xsd);
        writer.WriteString(StatusValue(manga.Status));
        writer.WriteEndElement();
        if (job.Categories.Count > 0)
        {
            writer.WriteStartElement("ty", "Categories", xsd);
            writer.WriteString(string.Join(", ", job.Categories));
            writer.WriteEndElement();
        }
        writer.WriteStartElement("mh", "SourceMihon", xsd);
        writer.WriteString(job.Source.Name);
        writer.WriteEndElement();
        writer.WriteEndElement();
        writer.WriteEndDocument();
    }

    private static string ChapterWebUrl(ChapterJob job)
    {
        if (Uri.TryCreate(job.Chapter.Url, UriKind.Absolute, out var absolute) && absolute.Scheme.StartsWith("http", StringComparison.Ordinal))
        {
            return absolute.AbsoluteUri;
        }
        try
        {
            return new Uri(job.Source.WebUrl(job.Manga.Url), job.Chapter.Url).AbsoluteUri;
        }
        catch (UriFormatException)
        {
            return job.Chapter.ExternalUrl ?? "";
        }
    }

    private static string StatusValue(MangaStatus status) => status switch
    {
        MangaStatus.Ongoing => "Ongoing",
        MangaStatus.Completed => "Completed",
        MangaStatus.Licensed => "Licensed",
        MangaStatus.PublishingFinished => "Publishing finished",
        MangaStatus.Cancelled => "Cancelled",
        MangaStatus.OnHiatus => "On hiatus",
        _ => "Unknown",
    };

    internal static void DeleteDirectory(string path)
    {
        try
        {
            if (Directory.Exists(path))
            {
                Directory.Delete(path, recursive: true);
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }

    internal static long NowMs() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
}
