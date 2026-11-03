using Windows.Graphics.Imaging;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Imaging;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Pages;

public sealed partial class BrushesProbePage : UserControl
{
    private static readonly string[] Keys =
    [
        "TzBackground", "TzCard", "TzChipBackground", "TzText", "TzTextSecondary", "TzTextTertiary",
        "TzStroke", "TzStrokeStrong", "TzAccent", "TzAccentSoft", "TzSkeleton", "TzReaderBackground", "TzReaderChrome",
    ];

    private readonly string? outputPath;

    public BrushesProbePage(string? outputPath)
    {
        InitializeComponent();
        this.outputPath = outputPath;
        Loaded += async (_, _) =>
        {
            foreach (var key in Keys)
            {
                if (FindName("Code" + key) is Border border)
                {
                    border.Background = Theme.Brush(key);
                }
            }
            await Task.Delay(350);
            if (this.outputPath is { } path)
            {
                try
                {
                    await CaptureAsync(path);
                }
                finally
                {
                    Application.Current.Exit();
                }
            }
        };
    }

    private async Task CaptureAsync(string path)
    {
        var bitmap = new RenderTargetBitmap();
        await bitmap.RenderAsync(Rows);
        var pixels = await bitmap.GetPixelsAsync();
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        using var stream = File.Create(path);
        var encoder = await BitmapEncoder.CreateAsync(BitmapEncoder.PngEncoderId, stream.AsRandomAccessStream());
        encoder.SetSoftwareBitmap(SoftwareBitmap.CreateCopyFromBuffer(pixels, BitmapPixelFormat.Bgra8, bitmap.PixelWidth, bitmap.PixelHeight));
        await encoder.FlushAsync();
    }
}
