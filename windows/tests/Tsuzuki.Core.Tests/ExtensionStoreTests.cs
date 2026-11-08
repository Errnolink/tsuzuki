using System.IO.Compression;
using System.Net;
using System.Text;
using Google.Protobuf;
using Tsuzuki.Core.Extensions;
using Xunit;

namespace Tsuzuki.Core.Tests;

public sealed class ExtensionStoreTests
{
    private const string IndexUrl = "https://github.com/keiyoushi/extensions/raw/repo/index.pb";

    private static byte[] Message(Action<CodedOutputStream> write)
    {
        using var buffer = new MemoryStream();
        var output = new CodedOutputStream(buffer);
        write(output);
        output.Flush();
        return buffer.ToArray();
    }

    private static byte[] ProtoIndex() => Message(index =>
    {
        index.WriteTag(1, WireFormat.WireType.LengthDelimited);
        index.WriteString("Keiyoushi");
        index.WriteTag(3, WireFormat.WireType.LengthDelimited);
        index.WriteString("9add655a");
        var extension = Message(e =>
        {
            e.WriteTag(1, WireFormat.WireType.LengthDelimited);
            e.WriteString("Asura Scans");
            e.WriteTag(2, WireFormat.WireType.LengthDelimited);
            e.WriteString("eu.kanade.tachiyomi.extension.en.asurascans");
            e.WriteTag(3, WireFormat.WireType.LengthDelimited);
            e.WriteBytes(ByteString.CopyFrom(Message(r =>
            {
                r.WriteTag(1, WireFormat.WireType.LengthDelimited);
                r.WriteString("https://example.org/tachiyomi-en.asurascans-v1.6.71.apk");
            })));
            e.WriteTag(4, WireFormat.WireType.LengthDelimited);
            e.WriteString("1.6");
            e.WriteTag(5, WireFormat.WireType.Varint);
            e.WriteInt64(71);
            e.WriteTag(6, WireFormat.WireType.LengthDelimited);
            e.WriteString("1.6.71");
            e.WriteTag(7, WireFormat.WireType.Varint);
            e.WriteEnum(3);
            e.WriteTag(8, WireFormat.WireType.LengthDelimited);
            e.WriteBytes(ByteString.CopyFrom(Message(s =>
            {
                s.WriteTag(1, WireFormat.WireType.Varint);
                s.WriteInt64(6247824327199706550);
                s.WriteTag(2, WireFormat.WireType.LengthDelimited);
                s.WriteString("Asura Scans");
                s.WriteTag(3, WireFormat.WireType.LengthDelimited);
                s.WriteString("en");
            })));
            e.WriteTag(99, WireFormat.WireType.LengthDelimited);
            e.WriteString("a field from a newer store format");
        });
        index.WriteTag(101, WireFormat.WireType.LengthDelimited);
        index.WriteBytes(ByteString.CopyFrom(Message(list =>
        {
            list.WriteTag(1, WireFormat.WireType.LengthDelimited);
            list.WriteBytes(ByteString.CopyFrom(extension));
        })));
    });

    private static byte[] Gzip(byte[] data)
    {
        using var output = new MemoryStream();
        using (var gzip = new GZipStream(output, CompressionLevel.Fastest))
        {
            gzip.Write(data);
        }
        return output.ToArray();
    }

    [Fact]
    public void A_gzipped_store_index_lists_its_extensions_with_their_sources_and_content_warning()
    {
        var index = ExtensionIndex.Parse(Gzip(ProtoIndex()), new Uri(IndexUrl));

        Assert.Equal("Keiyoushi", index.Name);
        Assert.Equal("9add655a", index.SigningKey);
        var asura = Assert.Single(index.Extensions);
        Assert.Equal("eu.kanade.tachiyomi.extension.en.asurascans", asura.Package);
        Assert.Equal(71, asura.VersionCode);
        Assert.Equal(ContentWarning.Nsfw, asura.Warning);
        Assert.Equal("en", asura.Language);
        var source = Assert.Single(asura.Sources);
        Assert.Equal(6247824327199706550, source.Id);
    }

    [Fact]
    public void A_legacy_json_index_points_apks_and_icons_next_to_the_index()
    {
        const string json = """
            [{"name":"Tachiyomi: Flame Comics","pkg":"eu.kanade.tachiyomi.extension.en.flamecomics","apk":"tachiyomi-en.flamecomics-v1.4.5.apk",
              "lang":"en","code":5,"version":"1.4.5","nsfw":0,
              "sources":[{"name":"Flame Comics","lang":"en","id":"8531542650987673943","baseUrl":"https://flamecomics.xyz"}]}]
            """;

        var index = ExtensionIndex.Parse(Encoding.UTF8.GetBytes(json), new Uri("https://example.org/repo/index.min.json"));

        var flame = Assert.Single(index.Extensions);
        Assert.Equal("Flame Comics", flame.Name);
        Assert.Equal("https://example.org/repo/apk/tachiyomi-en.flamecomics-v1.4.5.apk", flame.ApkUrl);
        Assert.Equal("https://example.org/repo/icon/eu.kanade.tachiyomi.extension.en.flamecomics.png", flame.IconUrl);
        Assert.Equal(8531542650987673943, flame.Sources[0].Id);
        Assert.True(ExtensionManager.IsSupportedLib(flame.Lib));
    }

    [Fact]
    public void A_mihon_add_store_link_opens_the_index_it_carries()
    {
        var url = ExtensionStores.DirectIndex("mihon://extension-store?url=https%3A%2F%2Fgithub.com%2Fkeiyoushi%2Fextensions%2Fraw%2Frepo%2Findex.pb");

        Assert.Equal(IndexUrl, url?.ToString());
    }

    [Fact]
    public async Task Pasting_a_store_website_finds_the_index_through_its_setup_guide()
    {
        var ct = TestContext.Current.CancellationToken;
        var site = new FakeWeb(new Dictionary<string, HttpResponseMessage>
        {
            ["https://keiyoushi.github.io/"] = Html("""<a href="/docs/guides/getting-started">Get started</a>"""),
            ["https://keiyoushi.github.io/docs/guides/getting-started"] =
                Html("""<a href="mihon://extension-store?url=https%3A%2F%2Fgithub.com%2Fkeiyoushi%2Fextensions%2Fraw%2Frepo%2Findex.pb">Add</a>"""),
            [IndexUrl] = new(HttpStatusCode.OK) { Content = new ByteArrayContent(ProtoIndex()) },
        });

        var (indexUrl, index) = await ExtensionStores.ResolveAsync(new HttpClient(site), "https://keiyoushi.github.io/", ct);

        Assert.Equal(IndexUrl, indexUrl.ToString());
        Assert.Equal("Keiyoushi", index.Name);
    }

    [Fact]
    public async Task A_website_without_a_store_is_refused_with_a_hint()
    {
        var ct = TestContext.Current.CancellationToken;
        var site = new FakeWeb(new Dictionary<string, HttpResponseMessage>
        {
            ["https://example.org/"] = Html("<p>Just a blog</p>"),
        });

        var error = await Assert.ThrowsAsync<InvalidDataException>(() => ExtensionStores.ResolveAsync(new HttpClient(site), "example.org", ct));

        Assert.Contains("index.pb", error.Message);
    }

    [Fact]
    public void Extensions_built_for_a_newer_library_are_not_offered_as_runnable()
    {
        Assert.False(ExtensionManager.IsSupportedLib("1.7"));
        Assert.False(ExtensionManager.IsSupportedLib("1.3"));
        Assert.True(ExtensionManager.IsSupportedLib("1.6"));
    }

    private static HttpResponseMessage Html(string body) => new(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "text/html") };

    private sealed class FakeWeb(Dictionary<string, HttpResponseMessage> pages) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(pages.TryGetValue(request.RequestUri!.ToString(), out var page) ? page : new HttpResponseMessage(HttpStatusCode.NotFound));
    }
}
