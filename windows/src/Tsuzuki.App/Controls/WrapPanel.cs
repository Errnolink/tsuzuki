using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Foundation;

namespace Tsuzuki.App.Controls;

public sealed partial class WrapPanel : Panel
{
    private double arrangedWidth = double.NaN;
    private double measuredFor = double.NaN;

    public double HorizontalSpacing { get; set; } = 6;
    public double VerticalSpacing { get; set; } = 6;

    protected override Size MeasureOverride(Size availableSize)
    {
        var limit = availableSize.Width;
        if (!double.IsNaN(arrangedWidth) && arrangedWidth < limit)
        {
            limit = arrangedWidth;
        }
        measuredFor = limit;
        double width = 0;
        foreach (var child in Children)
        {
            child.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
            width = Math.Max(width, child.DesiredSize.Width);
        }
        var (rowsWidth, height) = Layout(limit, arrange: false);
        width = Math.Max(width, rowsWidth);
        return new Size(double.IsInfinity(limit) ? width : Math.Min(width, limit), height);
    }

    protected override Size ArrangeOverride(Size finalSize)
    {
        if (!double.IsInfinity(finalSize.Width) && (double.IsNaN(arrangedWidth) || Math.Abs(finalSize.Width - arrangedWidth) > 0.5))
        {
            arrangedWidth = finalSize.Width;
            if (double.IsNaN(measuredFor) || Math.Abs(Layout(finalSize.Width, arrange: false).Height - Layout(measuredFor, arrange: false).Height) > 0.5)
            {
                DispatcherQueue.TryEnqueue(InvalidateMeasure);
            }
        }
        Layout(finalSize.Width, arrange: true);
        return finalSize;
    }

    private (double Width, double Height) Layout(double limit, bool arrange)
    {
        double x = 0, y = 0, rowHeight = 0, width = 0;
        foreach (var child in Children)
        {
            var size = child.DesiredSize;
            if (x > 0 && x + size.Width > limit)
            {
                y += rowHeight + VerticalSpacing;
                x = 0;
                rowHeight = 0;
            }
            if (arrange)
            {
                child.Arrange(new Rect(x, y, size.Width, size.Height));
            }
            x += size.Width + HorizontalSpacing;
            width = Math.Max(width, x - HorizontalSpacing);
            rowHeight = Math.Max(rowHeight, size.Height);
        }
        return (width, y + rowHeight);
    }
}
