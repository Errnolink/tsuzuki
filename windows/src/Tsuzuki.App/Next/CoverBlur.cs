using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Tsuzuki.Core.Images;
using Windows.Graphics.Imaging;

namespace Tsuzuki.App.Next;

public static class CoverBlur
{
    private const int Width = 64;
    private const int Radius = 3;
    private const int Passes = 3;
    private const int Capacity = 24;

    private static readonly object Gate = new();
    private static readonly LinkedList<(string Key, SoftwareBitmap Bitmap)> Recent = new();

    public static async Task<ImageSource?> LoadAsync(string? url, long sourceId, CancellationToken ct = default)
    {
        if (url is null || Services.Engine is not { } engine || !Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            return null;
        }
        try
        {
            var bitmap = Cached(url);
            if (bitmap is null)
            {
                var path = await engine.Covers.GetFileAsync(ImageRequest.Cover(sourceId, uri), ImagePriority.Visible, ct);
                bitmap = await Task.Run(() => DecodeBlurredAsync(path), ct);
                Remember(url, bitmap);
            }
            ct.ThrowIfCancellationRequested();
            var source = new SoftwareBitmapSource();
            await source.SetBitmapAsync(bitmap);
            return source;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or OperationCanceledException or HttpRequestException or InvalidOperationException or COMException)
        {
            return null;
        }
    }

    private static async Task<SoftwareBitmap> DecodeBlurredAsync(string path)
    {
        using var stream = File.OpenRead(path).AsRandomAccessStream();
        var decoder = await BitmapDecoder.CreateAsync(stream);
        var scale = (double)Width / Math.Max(1, decoder.OrientedPixelWidth);
        var width = Width;
        var height = Math.Max(1, (int)Math.Round(decoder.OrientedPixelHeight * scale));
        var data = await decoder.GetPixelDataAsync(
            BitmapPixelFormat.Bgra8,
            BitmapAlphaMode.Premultiplied,
            new BitmapTransform { ScaledWidth = (uint)width, ScaledHeight = (uint)height, InterpolationMode = BitmapInterpolationMode.Fant },
            ExifOrientationMode.RespectExifOrientation,
            ColorManagementMode.DoNotColorManage);
        var pixels = data.DetachPixelData();
        var scratch = new byte[pixels.Length];
        for (var i = 0; i < Passes; i++)
        {
            BoxPass(pixels, scratch, width, height, horizontal: true);
            BoxPass(scratch, pixels, width, height, horizontal: false);
        }
        return SoftwareBitmap.CreateCopyFromBuffer(pixels.AsBuffer(), BitmapPixelFormat.Bgra8, width, height, BitmapAlphaMode.Premultiplied);
    }

    private static void BoxPass(byte[] from, byte[] to, int width, int height, bool horizontal)
    {
        var lines = horizontal ? height : width;
        var length = horizontal ? width : height;
        var step = horizontal ? 4 : width * 4;
        var window = Radius * 2 + 1;
        Span<int> sum = stackalloc int[4];
        for (var line = 0; line < lines; line++)
        {
            var start = horizontal ? line * width * 4 : line * 4;
            sum.Clear();
            for (var k = -Radius; k <= Radius; k++)
            {
                var at = start + Math.Clamp(k, 0, length - 1) * step;
                for (var c = 0; c < 4; c++)
                {
                    sum[c] += from[at + c];
                }
            }
            for (var i = 0; i < length; i++)
            {
                var at = start + i * step;
                for (var c = 0; c < 4; c++)
                {
                    to[at + c] = (byte)(sum[c] / window);
                }
                var leaving = start + Math.Clamp(i - Radius, 0, length - 1) * step;
                var entering = start + Math.Clamp(i + Radius + 1, 0, length - 1) * step;
                for (var c = 0; c < 4; c++)
                {
                    sum[c] += from[entering + c] - from[leaving + c];
                }
            }
        }
    }

    private static SoftwareBitmap? Cached(string key)
    {
        lock (Gate)
        {
            for (var node = Recent.First; node is not null; node = node.Next)
            {
                if (node.Value.Key == key)
                {
                    Recent.Remove(node);
                    Recent.AddFirst(node);
                    return node.Value.Bitmap;
                }
            }
        }
        return null;
    }

    private static void Remember(string key, SoftwareBitmap bitmap)
    {
        lock (Gate)
        {
            Recent.AddFirst((key, bitmap));
            while (Recent.Count > Capacity)
            {
                Recent.RemoveLast();
            }
        }
    }
}
