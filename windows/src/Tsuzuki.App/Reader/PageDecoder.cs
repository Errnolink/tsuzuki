using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.Graphics.Imaging;

namespace Tsuzuki.App.Reader;

public sealed record PageSlice(SoftwareBitmapSource Source, int Height);

public sealed record DecodedPage(IReadOnlyList<PageSlice> Slices, int SourceWidth, int SourceHeight) : IDisposable
{
    private int released;

    public double Aspect => SourceHeight == 0 ? 1.4 : SourceHeight / (double)SourceWidth;
    public bool Landscape => SourceWidth > SourceHeight;

    public void Dispose()
    {
        if (Interlocked.Exchange(ref released, 1) != 0)
        {
            return;
        }
        var queue = DispatcherQueue.GetForCurrentThread();
        if (queue is not null)
        {
            ReleaseSources();
            return;
        }
        App.MainWindow.DispatcherQueue.TryEnqueue(ReleaseSources);
    }

    private void ReleaseSources()
    {
        foreach (var slice in Slices)
        {
            Native.Release(slice.Source);
        }
    }
}

public static class PageDecoder
{
    private const int MaxSliceHeight = 4096;
    private const long MaxPixelBytes = 96L * 1024 * 1024;

    private static readonly SemaphoreSlim DecodeSlots = new(3);

    public static async Task<DecodedPage> DecodeAsync(string path, int maxWidth, int maxHeight, CancellationToken ct)
    {
        List<SoftwareBitmap> bitmaps;
        int width;
        int height;
        await DecodeSlots.WaitAsync(ct);
        try
        {
            (bitmaps, width, height) = await Task.Run(() => DecodeBitmapsAsync(path, maxWidth, maxHeight, ct), ct);
        }
        finally
        {
            DecodeSlots.Release();
        }
        var slices = new List<PageSlice>(bitmaps.Count);
        try
        {
            foreach (var bitmap in bitmaps)
            {
                ct.ThrowIfCancellationRequested();
                var source = new SoftwareBitmapSource();
                slices.Add(new PageSlice(source, bitmap.PixelHeight));
                await source.SetBitmapAsync(bitmap);
            }
        }
        catch
        {
            foreach (var slice in slices)
            {
                Native.Release(slice.Source);
            }
            throw;
        }
        finally
        {
            foreach (var bitmap in bitmaps)
            {
                Native.Release(bitmap);
            }
        }
        return new DecodedPage(slices, width, height);
    }

    private static async Task<(List<SoftwareBitmap> Bitmaps, int Width, int Height)> DecodeBitmapsAsync(string path, int maxWidth, int maxHeight, CancellationToken ct)
    {
        using var file = File.OpenRead(path);
        using var stream = file.AsRandomAccessStream();
        var decoder = await BitmapDecoder.CreateAsync(stream);
        ct.ThrowIfCancellationRequested();
        var sw = (int)decoder.OrientedPixelWidth;
        var sh = (int)decoder.OrientedPixelHeight;
        var scale = Math.Min(1.0, Math.Min(maxWidth / (double)sw, maxHeight / (double)sh));
        var tw = Math.Max(1, (int)Math.Round(sw * scale));
        var th = Math.Max(1, (int)Math.Round(sh * scale));
        if ((long)tw * th * 4 > MaxPixelBytes)
        {
            var shrink = Math.Sqrt(MaxPixelBytes / ((double)tw * th * 4));
            tw = Math.Max(1, (int)(tw * shrink));
            th = Math.Max(1, (int)(th * shrink));
        }
        var transform = new BitmapTransform
        {
            ScaledWidth = (uint)tw,
            ScaledHeight = (uint)th,
            InterpolationMode = BitmapInterpolationMode.Fant,
        };
        if (th <= MaxSliceHeight)
        {
            SoftwareBitmap whole;
            try
            {
                whole = await decoder.GetSoftwareBitmapAsync(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied, transform, ExifOrientationMode.RespectExifOrientation, ColorManagementMode.DoNotColorManage);
            }
            catch (System.Runtime.InteropServices.COMException) when (transform.InterpolationMode != BitmapInterpolationMode.Linear)
            {
                transform.InterpolationMode = BitmapInterpolationMode.Linear;
                whole = await decoder.GetSoftwareBitmapAsync(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied, transform, ExifOrientationMode.RespectExifOrientation, ColorManagementMode.DoNotColorManage);
            }
            return ([whole], sw, sh);
        }
        var pixels = await decoder.GetPixelDataAsync(
            BitmapPixelFormat.Bgra8,
            BitmapAlphaMode.Premultiplied,
            transform,
            ExifOrientationMode.RespectExifOrientation,
            ColorManagementMode.DoNotColorManage);
        var bytes = pixels.DetachPixelData();
        var stride = tw * 4;
        var bitmaps = new List<SoftwareBitmap>();
        try
        {
            for (var top = 0; top < th; top += MaxSliceHeight)
            {
                ct.ThrowIfCancellationRequested();
                var height = Math.Min(MaxSliceHeight, th - top);
                bitmaps.Add(SoftwareBitmap.CreateCopyFromBuffer(
                    bytes.AsBuffer(top * stride, height * stride),
                    BitmapPixelFormat.Bgra8,
                    tw,
                    height,
                    BitmapAlphaMode.Premultiplied));
            }
        }
        catch
        {
            foreach (var bitmap in bitmaps)
            {
                bitmap.Dispose();
            }
            throw;
        }
        return (bitmaps, sw, sh);
    }
}
