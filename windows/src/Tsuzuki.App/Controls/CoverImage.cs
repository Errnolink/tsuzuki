using System.Collections.Concurrent;
using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Images;
using Windows.Graphics.Imaging;

namespace Tsuzuki.App.Controls;

public sealed partial class CoverImage : Grid
{
    private const long CacheBudgetBytes = 128 * 1024 * 1024;

    private static readonly object CacheGate = new();
    private static readonly LinkedList<(string Key, SoftwareBitmap Bitmap, long Bytes)> Cache = new();
    private static readonly Dictionary<string, LinkedListNode<(string Key, SoftwareBitmap Bitmap, long Bytes)>> CacheIndex = [];
    private static readonly ConcurrentDictionary<string, Lazy<Task<SoftwareBitmap?>>> Decoding = new();
    private static readonly NewestFirstGate DecodeSlots = new(Math.Clamp(Environment.ProcessorCount / 2, 3, 6));
    private static long cachedBytes;
    private static readonly ConcurrentDictionary<string, double> Ratios = new();

    public static double? RatioOf(string? url) => url is not null && Ratios.TryGetValue(url, out var ratio) ? ratio : null;

    public static readonly DependencyProperty UrlProperty = DependencyProperty.Register(
        nameof(Url), typeof(string), typeof(CoverImage), new PropertyMetadata(null, (d, _) => ((CoverImage)d).Load()));

    private readonly Image image = new() { Stretch = Stretch.UniformToFill, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
    private readonly Border placeholder = new();
    private int version;
    private CancellationTokenSource? loadCts;
    private string? shownUrl;
    private bool borrowed;

    public CoverImage()
    {
        placeholder.Background = Design.Theme.Brush("TzSkeleton");
        Children.Add(placeholder);
        Children.Add(image);
        Unloaded += (_, _) =>
        {
            version++;
            CancelLoad();
        };
        Loaded += (_, _) =>
        {
            if (image.Source is null)
            {
                Load();
            }
        };
    }

    public string? Url
    {
        get => (string?)GetValue(UrlProperty);
        set => SetValue(UrlProperty, value);
    }

    public int DecodeWidth { get; set; } = 240;

    public long SourceId { get; set; }

    public Stretch Stretch
    {
        get => image.Stretch;
        set => image.Stretch = value;
    }

    public ImageSource? Source => image.Source;

    public bool HasSource => image.Source is not null;

    public SoftwareBitmap? Bitmap { get; private set; }

    public void SetSource(ImageSource? source, string? url = null)
    {
        version++;
        CancelLoad();
        if (source is not null)
        {
            ReleaseSource();
            image.Source = source;
            borrowed = true;
            image.Opacity = 1;
            placeholder.Opacity = 0;
        }
        shownUrl = url;
        if (url is not null && Url != url)
        {
            SetValue(UrlProperty, url);
        }
    }

    public void RefreshQuietly()
    {
        if (!borrowed)
        {
            return;
        }
        shownUrl = null;
        Load();
    }

    private void ReleaseSource()
    {
        if (!borrowed)
        {
            Native.Release(image.Source);
        }
        borrowed = false;
    }

    public void Reload()
    {
        shownUrl = null;
        Load();
    }

    public event EventHandler? ImageLoaded;

    private void CancelLoad()
    {
        loadCts?.Cancel();
        loadCts?.Dispose();
        loadCts = null;
    }

    private bool OnScreen()
    {
        DependencyObject? node = this;
        while (node is not null)
        {
            if (node is UIElement { Visibility: Visibility.Collapsed })
            {
                return false;
            }
            node = VisualTreeHelper.GetParent(node);
        }
        return true;
    }

    private int PixelWidth()
    {
        var logical = ActualWidth > 0 ? Math.Min(ActualWidth, DecodeWidth) : DecodeWidth;
        var scale = XamlRoot?.RasterizationScale ?? 1.0;
        var pixels = (int)Math.Ceiling(logical * scale);
        return pixels <= 32 ? Math.Max(1, pixels) : (pixels + 31) / 32 * 32;
    }

    private async void Load()
    {
        var my = ++version;
        CancelLoad();
        var url = Url;
        if (url is not null && url == shownUrl && image.Source is not null)
        {
            return;
        }
        var hadSource = image.Source is not null;
        if (!hadSource)
        {
            placeholder.Opacity = 1;
        }
        if (url is null || Services.Engine is null)
        {
            ReleaseSource();
            image.Source = null;
            placeholder.Opacity = 1;
            return;
        }
        var cts = loadCts = new CancellationTokenSource();
        var ct = cts.Token;
        try
        {
            var bitmap = await DecodedAsync(Services.Engine.Covers, url, SourceId, PixelWidth(), OnScreen(), ct);
            if (my != version || bitmap is null)
            {
                return;
            }
            if (bitmap.PixelWidth > 0 && bitmap.PixelHeight > 0)
            {
                Ratios[url] = (double)bitmap.PixelWidth / bitmap.PixelHeight;
            }
            var source = new SoftwareBitmapSource();
            await source.SetBitmapAsync(bitmap);
            if (my != version)
            {
                Native.Release(source);
                return;
            }
            ReleaseSource();
            image.Source = source;
            Bitmap = bitmap;
            ImageLoaded?.Invoke(this, EventArgs.Empty);
            if (!hadSource)
            {
                await Motion.FadeIn(image, Motion.Short4);
            }
            else
            {
                image.Opacity = 1;
            }
            placeholder.Opacity = 0;
        }
        catch (Exception)
        {
            if (my == version && !hadSource)
            {
                placeholder.Opacity = 1;
            }
        }
    }

    private static async Task<SoftwareBitmap?> DecodedAsync(IImageCache cache, string url, long sourceId, int width, bool urgent, CancellationToken ct)
    {
        var key = url + "|" + width;
        if (Cached(key) is { } hit)
        {
            return hit;
        }
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            return null;
        }
        var path = Thumbnails.Existing(key) ?? await cache.GetFileAsync(ImageRequest.Cover(sourceId, uri), ImagePriority.Visible, ct);
        while (true)
        {
            ct.ThrowIfCancellationRequested();
            if (Cached(key) is { } ready)
            {
                return ready;
            }
            var created = false;
            var lazy = Decoding.GetOrAdd(key, _ =>
            {
                created = true;
                return new Lazy<Task<SoftwareBitmap?>>(() => DecodeAsync(path, width, key, urgent, ct));
            });
            if (!created && urgent)
            {
                DecodeSlots.Promote(key);
            }
            try
            {
                return await lazy.Value.WaitAsync(ct);
            }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            {
                Decoding.TryRemove(new KeyValuePair<string, Lazy<Task<SoftwareBitmap?>>>(key, lazy));
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                cache.Invalidate(ImageRequest.Cover(sourceId, uri));
                throw;
            }
        }
    }

    private static SoftwareBitmap? Cached(string key)
    {
        lock (CacheGate)
        {
            if (CacheIndex.TryGetValue(key, out var node))
            {
                Cache.Remove(node);
                Cache.AddFirst(node);
                return node.Value.Bitmap;
            }
        }
        return null;
    }

    private static async Task<SoftwareBitmap?> DecodeAsync(string path, int width, string key, bool urgent, CancellationToken ct)
    {
        try
        {
            await DecodeSlots.WaitAsync(key, urgent, ct).ConfigureAwait(false);
            try
            {
                ct.ThrowIfCancellationRequested();
                var bitmap = await Task.Run(() => Thumbnails.LoadAsync(path, width, key));
                Remember(key, bitmap);
                return bitmap;
            }
            finally
            {
                DecodeSlots.Release();
            }
        }
        finally
        {
            Decoding.TryRemove(key, out _);
        }
    }

    private static void Remember(string key, SoftwareBitmap bitmap)
    {
        var bytes = (long)bitmap.PixelWidth * bitmap.PixelHeight * 4;
        lock (CacheGate)
        {
            if (CacheIndex.TryGetValue(key, out var existing))
            {
                Cache.Remove(existing);
                CacheIndex.Remove(key);
                cachedBytes -= existing.Value.Bytes;
            }
            CacheIndex[key] = Cache.AddFirst((key, bitmap, bytes));
            cachedBytes += bytes;
            while (Cache.Count > 1 && cachedBytes > CacheBudgetBytes)
            {
                var last = Cache.Last!;
                Cache.RemoveLast();
                CacheIndex.Remove(last.Value.Key);
                cachedBytes -= last.Value.Bytes;
            }
        }
    }
}
