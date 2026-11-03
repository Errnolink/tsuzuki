using Microsoft.UI.Xaml.Media.Imaging;
using Windows.Graphics.Imaging;

namespace Tsuzuki.App.Reader;

public sealed record DecodedSlice(SoftwareBitmap Bitmap, int Width, int Height);

public sealed record DecodedPage(IReadOnlyList<DecodedSlice> Slices, int SourceWidth, int SourceHeight)
{
    public double Aspect => SourceHeight == 0 ? 1.4 : SourceHeight / (double)SourceWidth;
    public long Bytes => Slices.Sum(s => (long)s.Width * s.Height * 4);
}

public static class PageDecoder
{
    private const int MaxSliceHeight = 4096;

    public static Task<DecodedPage> DecodeAsync(string path, int maxWidth, int maxHeight, CancellationToken ct) => Task.Run(async () =>
    {
        using var file = File.OpenRead(path);
        var decoder = await BitmapDecoder.CreateAsync(file.AsRandomAccessStream());
        ct.ThrowIfCancellationRequested();
        var sw = (int)decoder.OrientedPixelWidth;
        var sh = (int)decoder.OrientedPixelHeight;
        var scale = Math.Min(1.0, Math.Min(maxWidth / (double)sw, maxHeight / (double)sh));
        var tw = Math.Max(1, (int)Math.Round(sw * scale));
        var th = Math.Max(1, (int)Math.Round(sh * scale));
        var slices = new List<DecodedSlice>();
        var sliceCount = (int)Math.Ceiling(th / (double)MaxSliceHeight);
        for (var i = 0; i < sliceCount; i++)
        {
            ct.ThrowIfCancellationRequested();
            var top = i * MaxSliceHeight;
            var height = Math.Min(MaxSliceHeight, th - top);
            var transform = new BitmapTransform
            {
                ScaledWidth = (uint)tw,
                ScaledHeight = (uint)th,
                InterpolationMode = BitmapInterpolationMode.Fant,
                Bounds = new BitmapBounds { X = 0, Y = (uint)top, Width = (uint)tw, Height = (uint)height },
            };
            var bitmap = await decoder.GetSoftwareBitmapAsync(
                BitmapPixelFormat.Bgra8,
                BitmapAlphaMode.Premultiplied,
                transform,
                ExifOrientationMode.RespectExifOrientation,
                ColorManagementMode.DoNotColorManage);
            slices.Add(new DecodedSlice(bitmap, tw, height));
        }
        return new DecodedPage(slices, sw, sh);
    }, ct);

    public static async Task<SoftwareBitmapSource> ToSourceAsync(SoftwareBitmap bitmap)
    {
        var source = new SoftwareBitmapSource();
        await source.SetBitmapAsync(bitmap);
        return source;
    }
}
