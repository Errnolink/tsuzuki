using System.Text;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.WeebCentral;
using Tsuzuki.Core.Tests.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests;

public sealed class ImageCacheTests
{
    private const string ChapterUrl = "/chapter/b82f842f-d581-49cf-bee1-6f9d732e57ad";

    private static string NewRoot()
    {
        var root = Path.Combine(TestLib.TempRoot, "images-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root);
        return root;
    }

    private static string AtHome(string baseUrl)
        => FakeMangaDexServer.ReadFixture("at-home.json").Replace(@"https:\/\/cmdxd98sb0x3yprd.mangadex.network", baseUrl, StringComparison.Ordinal);

    [Fact]
    public async Task A_page_list_with_a_new_at_home_node_reuses_the_cached_page_file()
    {
        var ct = TestContext.Current.CancellationToken;
        var nodes = new Queue<string>(["https://node-a.mangadex.network/token-a", "https://node-b.mangadex.network/token-b"]);
        var api = new FakeMangaDexServer((_, _) => FakeMangaDexServer.Raw(AtHome(nodes.Dequeue())));
        var source = FakeMangaDexServer.Source(api);
        var images = new RecordingHttp(_ => RecordingHttp.Bytes(Png(64)));
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), id => id == source.Id ? source : null, 1L << 30);

        var first = await source.GetPageListAsync(ChapterUrl, ct: ct);
        var firstPath = await cache.GetFileAsync(ImageRequest.Page(source.Id, first[0]), ImagePriority.Visible, ct);
        var second = await source.GetPageListAsync(ChapterUrl, refresh: true, ct: ct);
        var secondPath = await cache.GetFileAsync(ImageRequest.Page(source.Id, second[0]), ImagePriority.Visible, ct);

        Assert.NotEqual(first[0].ImageUrl, second[0].ImageUrl);
        Assert.Equal(firstPath, secondPath);
        Assert.Single(images.Requests);
        Assert.Equal(2, api.Requests.Count);
    }

    [Fact]
    public async Task Prefetch_and_load_of_one_chapter_share_one_at_home_call()
    {
        var ct = TestContext.Current.CancellationToken;
        var api = FakeMangaDexServer.FromFixtures();
        var source = FakeMangaDexServer.Source(api);

        var prefetched = await source.GetPageListAsync(ChapterUrl, ct: ct);
        var opened = await source.GetPageListAsync(ChapterUrl, ct: ct);

        Assert.Single(api.Requests);
        Assert.Equal(prefetched.Select(p => p.ImageUrl), opened.Select(p => p.ImageUrl));
    }

    [Fact]
    public async Task Page_downloads_use_the_source_image_request()
    {
        var ct = TestContext.Current.CancellationToken;
        var source = new WeebCentralSource(new HttpClient());
        var images = new RecordingHttp(_ => RecordingHttp.Bytes(Png(16)));
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), id => id == source.Id ? source : null, 1L << 30);
        var url = new Uri("https://scans.lastation.us/manga/Wind-Breaker/0178-001.png");

        await cache.GetFileAsync(ImageRequest.Page(source.Id, new PageRef(0, url, url.AbsoluteUri)), ImagePriority.Visible, ct);
        await cache.GetFileAsync(ImageRequest.Cover(source.Id, new Uri("https://temp.compsci88.com/cover/normal/01J76XYA1TQYKV6W4BCMR2WD5M.webp")), ImagePriority.Visible, ct);

        Assert.All(images.Requests, r => Assert.Equal(new Uri("https://weebcentral.com/"), r.Headers.Referrer));
        Assert.Equal(2, images.Requests.Count);
    }

    [Fact]
    public async Task Trim_keeps_recently_viewed_images_over_untouched_ones()
    {
        var ct = TestContext.Current.CancellationToken;
        var root = NewRoot();
        var images = new RecordingHttp(_ => RecordingHttp.Bytes(Png(1000)));
        var cache = new DiskImageCache(root, new HttpClient(images), _ => null, 1500);
        var viewed = ImageRequest.Cover(0, new Uri("https://example.org/viewed.jpg"));
        var untouched = ImageRequest.Cover(0, new Uri("https://example.org/untouched.jpg"));
        var viewedPath = await cache.GetFileAsync(viewed, ImagePriority.Visible, ct);
        var untouchedPath = await cache.GetFileAsync(untouched, ImagePriority.Visible, ct);
        var old = DateTime.UtcNow.AddDays(-30);
        File.SetLastWriteTimeUtc(viewedPath, old);
        File.SetLastAccessTimeUtc(viewedPath, old);
        File.SetLastWriteTimeUtc(untouchedPath, old.AddDays(10));
        File.SetLastAccessTimeUtc(untouchedPath, old.AddDays(10));

        await cache.GetFileAsync(viewed, ImagePriority.Visible, ct);
        cache.Trim();

        Assert.True(File.Exists(viewedPath));
        Assert.False(File.Exists(untouchedPath));
    }

    private static byte[] Png(int length)
    {
        var bytes = new byte[length];
        byte[] magic = [0x89, (byte)'P', (byte)'N', (byte)'G', 0x0D, 0x0A, 0x1A, 0x0A];
        magic.CopyTo(bytes, 0);
        return bytes;
    }

    [Fact]
    public async Task An_html_page_instead_of_an_image_is_not_cached_and_a_retry_can_succeed()
    {
        var ct = TestContext.Current.CancellationToken;
        var portal = true;
        var images = new RecordingHttp(_ => portal
            ? new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent("<html>Sign in to Wi-Fi</html>", Encoding.UTF8, "text/html") }
            : RecordingHttp.Bytes(Png(32)));
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), _ => null, 1L << 30);
        var fetches = new List<ImageFetch>();
        cache.Fetched += (_, f) => { lock (fetches) fetches.Add(f); };
        var page = ImageRequest.Cover(0, new Uri("https://node.mangadex.network/data/hash/1.jpg"));

        await Assert.ThrowsAsync<HttpRequestException>(() => cache.GetFileAsync(page, ImagePriority.Visible, ct));
        Assert.False(File.Exists(cache.PathFor(page)));
        Assert.Empty(Directory.EnumerateFiles(Path.GetDirectoryName(Path.GetDirectoryName(cache.PathFor(page)))!, "*", SearchOption.AllDirectories));
        Assert.False(Assert.Single(fetches).Success);

        portal = false;
        var path = await cache.GetFileAsync(page, ImagePriority.Visible, ct);
        Assert.Equal(Png(32), await File.ReadAllBytesAsync(path, ct));
    }

    [Fact]
    public async Task An_invalidated_image_is_downloaded_again()
    {
        var ct = TestContext.Current.CancellationToken;
        var images = new RecordingHttp(_ => RecordingHttp.Bytes(Png(32)));
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), _ => null, 1L << 30);
        var page = ImageRequest.Cover(0, new Uri("https://example.org/page.png"));
        await cache.GetFileAsync(page, ImagePriority.Visible, ct);

        cache.Invalidate(page);
        await cache.GetFileAsync(page, ImagePriority.Visible, ct);

        Assert.Equal(2, images.Requests.Count);
    }

    [Fact]
    public async Task A_cover_the_site_refuses_is_not_requested_again_on_every_scroll()
    {
        var ct = TestContext.Current.CancellationToken;
        var images = new RecordingHttp(_ => new System.Net.Http.HttpResponseMessage(System.Net.HttpStatusCode.Forbidden));
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), _ => null, 1L << 30);
        var cover = ImageRequest.Cover(0, new Uri("https://example.org/hotlinked.webp"));

        for (var i = 0; i < 3; i++)
        {
            await Assert.ThrowsAsync<HttpRequestException>(() => cache.GetFileAsync(cover, ImagePriority.Visible, ct));
        }

        Assert.Single(images.Requests);
    }

    [Fact]
    public async Task A_body_that_stalls_after_the_headers_fails_instead_of_hanging()
    {
        var ct = TestContext.Current.CancellationToken;
        var images = new StallingHttp();
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), _ => null, 1L << 30) { IdleReadTimeout = TimeSpan.FromMilliseconds(300) };
        var page = ImageRequest.Cover(0, new Uri("https://example.org/stall.png"));

        var clock = System.Diagnostics.Stopwatch.StartNew();
        await Assert.ThrowsAsync<HttpRequestException>(() => cache.GetFileAsync(page, ImagePriority.Visible, ct).WaitAsync(TimeSpan.FromSeconds(10), ct));

        Assert.True(clock.Elapsed < TimeSpan.FromSeconds(5));
        Assert.False(File.Exists(cache.PathFor(page)));
    }

    [Fact]
    public async Task A_download_nobody_waits_for_any_more_is_cancelled()
    {
        var ct = TestContext.Current.CancellationToken;
        var images = new StallingHttp();
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), _ => null, 1L << 30);
        using var leave = new CancellationTokenSource();
        var page = ImageRequest.Cover(0, new Uri("https://example.org/scrolled-past.png"));

        var waiting = cache.GetFileAsync(page, ImagePriority.Visible, leave.Token);
        await images.BodyStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), ct);
        leave.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => waiting);
        await images.BodyAbandoned.Task.WaitAsync(TimeSpan.FromSeconds(5), ct);
    }

    [Fact]
    public async Task A_queued_prefetch_moves_to_the_visible_pool_when_the_page_comes_on_screen()
    {
        var ct = TestContext.Current.CancellationToken;
        var stalled = new Uri("https://example.org/stalled.png");
        var images = new StallingHttp(request => request.RequestUri == stalled);
        var cache = new DiskImageCache(NewRoot(), new HttpClient(images), _ => null, 1L << 30, visibleConcurrency: 2, backgroundConcurrency: 1);
        cache.Prefetch([ImageRequest.Cover(0, stalled)]);
        await images.BodyStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), ct);
        var page = ImageRequest.Cover(0, new Uri("https://example.org/next.png"));
        cache.Prefetch([page]);

        var path = await cache.GetFileAsync(page, ImagePriority.Visible, ct).WaitAsync(TimeSpan.FromSeconds(5), ct);

        Assert.True(File.Exists(path));
    }

    private sealed class StallingHttp(Func<HttpRequestMessage, bool>? stall = null) : HttpMessageHandler
    {
        public TaskCompletionSource BodyStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public TaskCompletionSource BodyAbandoned { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (stall != null && !stall(request)) return Task.FromResult(RecordingHttp.Bytes(Png(32)));
            var body = new StallingStream(this, cancellationToken);
            return Task.FromResult(new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StreamContent(body) });
        }

        private sealed class StallingStream(StallingHttp owner, CancellationToken requestToken) : Stream
        {
            private bool sentHead;

            public override bool CanRead => true;
            public override bool CanSeek => false;
            public override bool CanWrite => false;
            public override long Length => throw new NotSupportedException();
            public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
            public override void Flush() { }
            public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
            public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
            public override void SetLength(long value) => throw new NotSupportedException();
            public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();

            public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
            {
                if (!sentHead)
                {
                    sentHead = true;
                    var head = Png(8);
                    head.CopyTo(buffer);
                    owner.BodyStarted.TrySetResult();
                    return head.Length;
                }
                using var linked = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken, requestToken);
                try
                {
                    await Task.Delay(Timeout.Infinite, linked.Token);
                }
                catch (OperationCanceledException)
                {
                    owner.BodyAbandoned.TrySetResult();
                    throw;
                }
                return 0;
            }
        }
    }
}
