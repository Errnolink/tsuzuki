namespace Tsuzuki.Presentation;

public readonly record struct Rgb(byte R, byte G, byte B)
{
    public static readonly Rgb White = new(255, 255, 255);

    public double Luminance
    {
        get
        {
            static double Channel(byte v8)
            {
                var v = v8 / 255.0;
                return v <= 0.04045 ? v / 12.92 : Math.Pow((v + 0.055) / 1.055, 2.4);
            }
            return 0.2126 * Channel(R) + 0.7152 * Channel(G) + 0.0722 * Channel(B);
        }
    }

    public double ContrastWith(Rgb other)
    {
        var a = Luminance;
        var b = other.Luminance;
        return (Math.Max(a, b) + 0.05) / (Math.Min(a, b) + 0.05);
    }

    public (double H, double S, double V) ToHsv()
    {
        double r = R / 255.0, g = G / 255.0, b = B / 255.0;
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

    public static Rgb FromHsv(double h, double s, double v)
    {
        h = ((h % 360) + 360) % 360;
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
        return new((byte)Math.Round((r + m) * 255), (byte)Math.Round((g + m) * 255), (byte)Math.Round((b + m) * 255));
    }
}

public sealed record CoverTint(Rgb Accent, Rgb Card, Rgb Glass);

public static class CoverPalette
{
    public const double TextContrast = 4.5;
    private const double Margin = 0.1;

    public static Rgb? Dominant(ReadOnlySpan<byte> bgra)
    {
        var weights = new double[36];
        var sums = new (double X, double Y, double S, double V, int N)[36];
        for (var i = 0; i + 3 < bgra.Length; i += 4)
        {
            var (h, s, v) = new Rgb(bgra[i + 2], bgra[i + 1], bgra[i]).ToHsv();
            if (s < 0.18 || v < 0.18)
            {
                continue;
            }
            var bucket = (int)(h / 10) % 36;
            weights[bucket] += s * v;
            var angle = h * Math.PI / 180;
            var (x, y, ss, vv, n) = sums[bucket];
            sums[bucket] = (x + Math.Cos(angle), y + Math.Sin(angle), ss + s, vv + v, n + 1);
        }
        var best = -1;
        for (var b = 0; b < 36; b++)
        {
            var merged = weights[b] + 0.5 * (weights[(b + 35) % 36] + weights[(b + 1) % 36]);
            var bestMerged = best < 0 ? -1 : weights[best] + 0.5 * (weights[(best + 35) % 36] + weights[(best + 1) % 36]);
            if (weights[b] > 0 && merged > bestMerged)
            {
                best = b;
            }
        }
        if (best < 0 || sums[best].N < 6)
        {
            return null;
        }
        var (sx, sy, sumS, sumV, count) = sums[best];
        var hue = Math.Atan2(sy, sx) * 180 / Math.PI;
        return Rgb.FromHsv(hue < 0 ? hue + 360 : hue, sumS / count, sumV / count);
    }

    public static CoverTint? Tint(ReadOnlySpan<byte> bgra) => Dominant(bgra) is { } dominant ? Tint(dominant) : null;

    public static CoverTint Tint(Rgb dominant)
    {
        var (hue, sat, value) = dominant.ToHsv();
        var accent = Darken(hue, Math.Clamp(sat, 0.55, 0.90), Math.Clamp(value, 0.50, 0.62));
        var glassSat = Math.Clamp(sat, 0.45, 0.80);
        var glass = Darken(hue, glassSat, 0.62);
        var cardSat = Math.Clamp(sat * 1.05, 0.60, 0.90);
        var card = Darken(hue, cardSat, glass.ToHsv().V * 0.78);
        return new CoverTint(accent, card, glass);
    }

    private static Rgb Darken(double hue, double sat, double value)
    {
        var color = Rgb.FromHsv(hue, sat, value);
        while (value > 0.04 && color.ContrastWith(Rgb.White) < TextContrast + Margin)
        {
            value -= 0.01;
            color = Rgb.FromHsv(hue, sat, value);
        }
        return color;
    }
}
