using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Foundation;

namespace Tsuzuki.App.Controls;

public sealed partial class AspectBox : Panel
{
    public double Ratio { get; set; } = 1.5;

    protected override Size MeasureOverride(Size availableSize)
    {
        var width = double.IsInfinity(availableSize.Width) ? 160 : availableSize.Width;
        var size = new Size(width, width * Ratio);
        foreach (var child in Children)
        {
            child.Measure(size);
        }
        return size;
    }

    protected override Size ArrangeOverride(Size finalSize)
    {
        foreach (var child in Children)
        {
            child.Arrange(new Rect(0, 0, finalSize.Width, finalSize.Height));
        }
        return finalSize;
    }
}
