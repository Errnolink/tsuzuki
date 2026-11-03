using Microsoft.UI.Composition;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace Tsuzuki.App.Design;

public sealed class TzLiveBrush : XamlCompositionBrushBase
{
    private readonly string key;
    private readonly string? palette;

    public TzLiveBrush(string key, string? palette)
    {
        this.key = key;
        this.palette = palette;
        FallbackColor = Effective();
    }

    internal void Refresh()
    {
        var color = Effective();
        if (FallbackColor == color)
        {
            return;
        }
        FallbackColor = color;
        if (CompositionBrush is CompositionColorBrush solid)
        {
            solid.Color = color;
        }
    }

    protected override void OnConnected()
    {
        if (App.MainWindow?.Content is { } root)
        {
            CompositionBrush = Microsoft.UI.Xaml.Hosting.ElementCompositionPreview.GetElementVisual(root).Compositor.CreateColorBrush(Effective());
        }
    }

    protected override void OnDisconnected()
    {
        CompositionBrush = null;
    }

    private Color Effective()
    {
        var (color, opacity) = Theme.Lookup(key, palette);
        return opacity >= 0.999 ? color : Color.FromArgb((byte)Math.Clamp(color.A * opacity, 0, 255), color.R, color.G, color.B);
    }
}
