using System.Runtime.InteropServices.WindowsRuntime;
using System.Security.Cryptography;
using System.Text;
using Windows.Graphics.Imaging;
using Windows.Storage.Streams;

namespace Tsuzuki.App.Controls;

public static class Thumbnails
{
    private static readonly TimeSpan KeepUnused = TimeSpan.FromDays(30);
    private static string? root;

    public static void Configure(string directory)
    {
        root = directory;
        Directory.CreateDirectory(directory);
        _ = Task.Run(() => Trim(directory));
    }

    public static string? Existing(string key) => PathFor(key) is { } path && File.Exists(path) ? path : null;

    public static async Task<SoftwareBitmap> LoadAsync(string sourcePath, int width, string key)
    {
        if (Existing(key) is { } thumb)
        {
            try
            {
                return await DecodeAsync(thumb, int.MaxValue);
            }
            catch (Exception)
            {
                TryDelete(thumb);
                if (sourcePath == thumb)
                {
                    throw;
                }
            }
        }
        var bitmap = await DecodeAsync(sourcePath, width);
        if (PathFor(key) is { } target)
        {
            await SaveAsync(bitmap, target);
        }
        return bitmap;
    }

    private static string? PathFor(string key) =>
        root is null ? null : Path.Combine(root, Convert.ToHexStringLower(SHA1.HashData(Encoding.UTF8.GetBytes(key))) + ".jpg");

    private static async Task<SoftwareBitmap> DecodeAsync(string path, int width)
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
            ColorManagementMode.DoNotColorManage);
        return SoftwareBitmap.CreateCopyFromBuffer(pixels.DetachPixelData().AsBuffer(), BitmapPixelFormat.Bgra8, targetWidth, targetHeight, BitmapAlphaMode.Premultiplied);
    }

    private static async Task SaveAsync(SoftwareBitmap bitmap, string path)
    {
        var temp = path + "." + Environment.CurrentManagedThreadId + ".tmp";
        try
        {
            using (var memory = new InMemoryRandomAccessStream())
            {
                var options = new BitmapPropertySet { ["ImageQuality"] = new BitmapTypedValue(0.9, Windows.Foundation.PropertyType.Single) };
                var encoder = await BitmapEncoder.CreateAsync(BitmapEncoder.JpegEncoderId, memory, options);
                encoder.SetSoftwareBitmap(SoftwareBitmap.Convert(bitmap, BitmapPixelFormat.Bgra8, BitmapAlphaMode.Ignore));
                await encoder.FlushAsync();
                memory.Seek(0);
                await using var file = File.Create(temp);
                await memory.AsStreamForRead().CopyToAsync(file);
            }
            File.Move(temp, path, overwrite: true);
        }
        catch (Exception)
        {
            TryDelete(temp);
        }
    }

    private static void Trim(string directory)
    {
        try
        {
            var cutoff = DateTime.UtcNow - KeepUnused;
            foreach (var file in Directory.EnumerateFiles(directory))
            {
                if (File.GetLastAccessTimeUtc(file) < cutoff && File.GetLastWriteTimeUtc(file) < cutoff)
                {
                    File.Delete(file);
                }
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }

    private static void TryDelete(string path)
    {
        try
        {
            File.Delete(path);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
        }
    }
}
