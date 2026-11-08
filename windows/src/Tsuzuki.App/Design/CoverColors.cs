using System.Runtime.InteropServices.WindowsRuntime;
using Tsuzuki.Core.Images;
using Tsuzuki.Presentation;
using Windows.Graphics.Imaging;
using Windows.UI;

namespace Tsuzuki.App.Design;

public static class CoverColors
{
    private static readonly Dictionary<string, CoverTint?> Cache = [];

    public static async Task<CoverTint?> TintAsync(string? url, long sourceId)
    {
        if (url is null || Services.Engine is null || !Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            return null;
        }
        if (Cache.TryGetValue(url, out var known))
        {
            return known;
        }
        try
        {
            var path = await Services.Engine.Covers.GetFileAsync(ImageRequest.Cover(sourceId, uri), ImagePriority.Visible);
            var pixels = await Task.Run(async () =>
            {
                using var file = File.OpenRead(path);
                var decoder = await BitmapDecoder.CreateAsync(file.AsRandomAccessStream());
                var transform = new BitmapTransform { ScaledWidth = 24, ScaledHeight = 36, InterpolationMode = BitmapInterpolationMode.Fant };
                var data = await decoder.GetPixelDataAsync(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied, transform,
                    ExifOrientationMode.IgnoreExifOrientation, ColorManagementMode.DoNotColorManage);
                return data.DetachPixelData();
            });
            var tint = CoverPalette.Tint(pixels);
            Cache[url] = tint;
            return tint;
        }
        catch (Exception)
        {
            Cache[url] = null;
            return null;
        }
    }

    public static CoverTint? Known(string? url) => url is not null && Cache.TryGetValue(url, out var tint) ? tint : null;

    public static CoverTint? TintOf(SoftwareBitmap? bitmap, string? url)
    {
        if (url is null || bitmap is null || bitmap.PixelWidth <= 0 || bitmap.PixelHeight <= 0)
        {
            return Known(url);
        }
        if (Cache.TryGetValue(url, out var known))
        {
            return known;
        }
        try
        {
            var width = bitmap.PixelWidth;
            var height = bitmap.PixelHeight;
            var bytes = new byte[width * height * 4];
            bitmap.CopyToBuffer(bytes.AsBuffer());
            var step = Math.Max(1, (int)Math.Sqrt(width * height / 900.0));
            var sample = new List<byte>(4 * (width / step + 1) * (height / step + 1));
            for (var y = 0; y < height; y += step)
            {
                for (var x = 0; x < width; x += step)
                {
                    var i = (y * width + x) * 4;
                    sample.AddRange(bytes.AsSpan(i, 4));
                }
            }
            var tint = CoverPalette.Tint([.. sample]);
            Cache[url] = tint;
            return tint;
        }
        catch (Exception)
        {
            return null;
        }
    }

    public static Color ToColor(this Rgb rgb, byte alpha = 255) => Color.FromArgb(alpha, rgb.R, rgb.G, rgb.B);
}
