using Tsuzuki.Core.Images;
using Windows.Graphics.Imaging;
using Windows.UI;

namespace Tsuzuki.App.Design;

public static class CoverColors
{
    private static readonly Dictionary<string, Color?> Cache = [];

    public static async Task<Color?> AccentAsync(string? url)
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
            var path = await Services.Engine.Covers.GetFileAsync(uri, ImagePriority.Visible);
            var pixels = await Task.Run(async () =>
            {
                using var file = File.OpenRead(path);
                var decoder = await BitmapDecoder.CreateAsync(file.AsRandomAccessStream());
                var transform = new BitmapTransform { ScaledWidth = 24, ScaledHeight = 36, InterpolationMode = BitmapInterpolationMode.Fant };
                var data = await decoder.GetPixelDataAsync(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied, transform,
                    ExifOrientationMode.IgnoreExifOrientation, ColorManagementMode.DoNotColorManage);
                return data.DetachPixelData();
            });
            var color = Pick(pixels);
            Cache[url] = color;
            return color;
        }
        catch (Exception)
        {
            Cache[url] = null;
            return null;
        }
    }

    private static Color? Pick(byte[] bgra)
    {
        var weights = new double[36];
        var sums = new (double H, double S, double V, int N)[36];
        for (var i = 0; i + 3 < bgra.Length; i += 4)
        {
            var (h, s, v) = ToHsv(bgra[i + 2], bgra[i + 1], bgra[i]);
            if (s < 0.18 || v < 0.18 || v > 0.98)
            {
                continue;
            }
            var bucket = (int)(h / 10) % 36;
            weights[bucket] += s * v;
            sums[bucket] = (sums[bucket].H + h, sums[bucket].S + s, sums[bucket].V + v, sums[bucket].N + 1);
        }
        var best = -1;
        for (var b = 0; b < 36; b++)
        {
            if (best < 0 || weights[b] > weights[best])
            {
                best = b;
            }
        }
        if (best < 0 || sums[best].N < 6)
        {
            return null;
        }
        var n = sums[best].N;
        var hue = sums[best].H / n;
        var sat = Math.Clamp(sums[best].S / n, 0.55, 0.90);
        var val = Math.Clamp(sums[best].V / n, 0.50, 0.62);
        return FromHsv(hue, sat, val);
    }

    private static (double H, double S, double V) ToHsv(byte r8, byte g8, byte b8)
    {
        double r = r8 / 255.0, g = g8 / 255.0, b = b8 / 255.0;
        var max = Math.Max(r, Math.Max(g, b));
        var min = Math.Min(r, Math.Min(g, b));
        var d = max - min;
        double h = 0;
        if (d > 0)
        {
            if (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * (((b - r) / d) + 2);
            else h = 60 * (((r - g) / d) + 4);
        }
        if (h < 0) h += 360;
        return (h, max == 0 ? 0 : d / max, max);
    }

    public static Color FromHsv(double h, double s, double v)
    {
        var c = v * s;
        var x = c * (1 - Math.Abs((h / 60) % 2 - 1));
        var m = v - c;
        var (r, g, b) = h switch
        {
            < 60 => (c, x, 0.0),
            < 120 => (x, c, 0.0),
            < 180 => (0.0, c, x),
            < 240 => (0.0, x, c),
            < 300 => (x, 0.0, c),
            _ => (c, 0.0, x),
        };
        return Color.FromArgb(255, (byte)((r + m) * 255), (byte)((g + m) * 255), (byte)((b + m) * 255));
    }

    public static Color EnsureReadable(Color accent)
    {
        var (hue, sat, value) = ToHsv(accent.R, accent.G, accent.B);
        while (value > 0.04 && Contrast(Microsoft.UI.Colors.White, FromHsv(hue, sat, value)) < 4.6)
        {
            value -= 0.02;
        }
        return FromHsv(hue, sat, value);
    }

    public static double Contrast(Color a, Color b)
    {
        var la = Luminance(a);
        var lb = Luminance(b);
        return (Math.Max(la, lb) + 0.05) / (Math.Min(la, lb) + 0.05);
    }

    private static double Luminance(Color c)
    {
        double channel(byte v8)
        {
            var v = v8 / 255.0;
            return v <= 0.04045 ? v / 12.92 : Math.Pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channel(c.R) + 0.7152 * channel(c.G) + 0.0722 * channel(c.B);
    }
}
