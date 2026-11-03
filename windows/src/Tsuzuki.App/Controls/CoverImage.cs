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
    private static readonly ConcurrentDictionary<string, Task<SoftwareBitmap?>> Decoding = new();
    private static readonly SemaphoreSlim DecodeSlots = new(3);
    private static long cachedBytes;

    public static readonly DependencyProperty UrlProperty = DependencyProperty.Register(
        nameof(Url), typeof(string), typeof(CoverImage), new PropertyMetadata(null, (d, _) => ((CoverImage)d).Load()));

    private readonly Image image = new() { Stretch = Stretch.UniformToFill, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
    private readonly Border placeholder = new();
    private int version;
    private string? shownUrl;
    private bool borrowed;

    public CoverImage()
    {
        placeholder.Background = Design.Theme.Brush("TzSkeleton");
        Children.Add(placeholder);
        Children.Add(image);
        Unloaded += (_, _) => version++;
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

    public Stretch Stretch
    {
        get => image.Stretch;
        set => image.Stretch = value;
    }

    public ImageSource? Source => image.Source;

    public bool HasSource => image.Source is not null;

    public void SetSource(ImageSource? source, string? url = null)
    {
        version++;
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
            (image.Source as IDisposable)?.Dispose();
        }
        borrowed = false;
    }

    public void Reload()
    {
        shownUrl = null;
        Load();
    }

    public event EventHandler? ImageLoaded;


    private async void Load()
    {
        var my = ++version;
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
        try
        {
            var bitmap = await DecodedAsync(Services.Engine.Covers, url, DecodeWidth);
            if (my != version || bitmap is null)
            {
                return;
            }
            var source = new SoftwareBitmapSource();
            await source.SetBitmapAsync(bitmap);
            if (my != version)
            {
                source.Dispose();
                return;
            }
            ReleaseSource();
            image.Source = source;
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

    private static async Task<SoftwareBitmap?> DecodedAsync(IImageCache cache, string url, int width)
    {
        var key = url + "|" + width;
        lock (CacheGate)
        {
            if (CacheIndex.TryGetValue(key, out var node))
            {
                Cache.Remove(node);
                Cache.AddFirst(node);
                return node.Value.Bitmap;
            }
        }
        return await Decoding.GetOrAdd(key, _ => Task.Run(() => DecodeAsync(cache, url, width, key)));
    }

    private static async Task<SoftwareBitmap?> DecodeAsync(IImageCache cache, string url, int width, string key)
    {
        try
        {
            if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
            {
                return null;
            }
            await DecodeSlots.WaitAsync();
            try
            {
                var path = await cache.GetFileAsync(uri, ImagePriority.Visible);
                var bitmap = await Task.Run(async () =>
                {
                    using var stream = File.OpenRead(path).AsRandomAccessStream();
                    var decoder = await BitmapDecoder.CreateAsync(stream);
                    var sourceWidth = decoder.OrientedPixelWidth;
                    var sourceHeight = decoder.OrientedPixelHeight;
                    var scale = Math.Min(1.0, (double)width / sourceWidth);
                    var targetWidth = Math.Max(1, (int)Math.Round(sourceWidth * scale));
                    var targetHeight = Math.Max(1, (int)Math.Round(sourceHeight * scale));
                    var pixels = await decoder.GetPixelDataAsync(
                        BitmapPixelFormat.Bgra8,
                        BitmapAlphaMode.Premultiplied,
                        new BitmapTransform
                        {
                            ScaledWidth = (uint)targetWidth,
                            ScaledHeight = (uint)targetHeight,
                            InterpolationMode = BitmapInterpolationMode.Fant,
                        },
                        ExifOrientationMode.RespectExifOrientation,
                        ColorManagementMode.ColorManageToSRgb);
                    return SoftwareBitmap.CreateCopyFromBuffer(
                        pixels.DetachPixelData().AsBuffer(),
                        BitmapPixelFormat.Bgra8,
                        targetWidth,
                        targetHeight,
                        BitmapAlphaMode.Premultiplied);
                });
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
