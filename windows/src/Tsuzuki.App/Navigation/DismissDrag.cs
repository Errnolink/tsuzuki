using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Input;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Tsuzuki.App.Design;
using Tsuzuki.Presentation;
using Windows.Foundation;

namespace Tsuzuki.App.Navigation;

public sealed class DismissDrag
{
    private const double StartSlop = 8;
    private readonly UserControl page;
    private readonly Func<bool> atTop;
    private Pointer? pointer;
    private UIElement? capture;
    private Point start;
    private bool dragging;
    private double offset;
    private long lastTicks;
    private double lastOffset;
    private double velocity;
    private FrameworkElement? below;

    private DismissDrag(UserControl page, Func<bool> atTop)
    {
        this.page = page;
        this.atTop = atTop;
    }

    public static void Attach(UserControl page, UIElement handle, Func<bool> atTop)
    {
        var drag = new DismissDrag(page, atTop);
        handle.AddHandler(UIElement.PointerPressedEvent, new PointerEventHandler(drag.OnPressed), true);
        page.AddHandler(UIElement.PointerMovedEvent, new PointerEventHandler(drag.OnMoved), true);
        page.AddHandler(UIElement.PointerReleasedEvent, new PointerEventHandler(drag.OnReleased), true);
        page.AddHandler(UIElement.PointerCanceledEvent, new PointerEventHandler(drag.OnReleased), true);
        page.AddHandler(UIElement.PointerCaptureLostEvent, new PointerEventHandler(drag.OnCaptureLost), true);
    }

    private Visual Surface => Motion.VisualOf((UIElement)page.Content);

    private void OnPressed(object sender, PointerRoutedEventArgs e)
    {
        if (Motion.Reduced || pointer is not null || !atTop() || StartsOnControl(e.OriginalSource as DependencyObject))
        {
            return;
        }
        var point = e.GetCurrentPoint(page);
        if (point.PointerDeviceType == PointerDeviceType.Mouse && !point.Properties.IsLeftButtonPressed)
        {
            return;
        }
        pointer = e.Pointer;
        capture = page;
        start = point.Position;
        dragging = false;
        offset = 0;
        lastOffset = 0;
        velocity = 0;
        lastTicks = Environment.TickCount64;
    }

    private void OnMoved(object sender, PointerRoutedEventArgs e)
    {
        if (pointer is null || e.Pointer.PointerId != pointer.PointerId)
        {
            return;
        }
        var at = e.GetCurrentPoint(page).Position;
        var dy = at.Y - start.Y;
        var dx = Math.Abs(at.X - start.X);
        if (!dragging)
        {
            if (dy < -StartSlop || dx > StartSlop * 1.5)
            {
                pointer = null;
                return;
            }
            if (dy < StartSlop || dy < dx)
            {
                return;
            }
            dragging = true;
            capture!.CapturePointer(e.Pointer);
            below = App.MainWindow.Nav.Below;
            if (below is not null)
            {
                Motion.VisualOf(below).Opacity = 1;
            }
            var size = page.ActualSize;
            Surface.CenterPoint = new Vector3(size.X / 2, size.Y * 0.35f, 0);
        }
        offset = Math.Max(0, dy - StartSlop);
        var now = Environment.TickCount64;
        var dt = Math.Max(1, now - lastTicks);
        velocity = 0.7 * velocity + 0.3 * ((offset - lastOffset) * 1000.0 / dt);
        lastOffset = offset;
        lastTicks = now;
        var pose = DragPose.At(offset, page.ActualHeight);
        Surface.Scale = new Vector3((float)pose.Scale, (float)pose.Scale, 1);
        Surface.Properties.InsertVector3("Translation", new Vector3(0, (float)pose.Lift, 0));
        e.Handled = true;
    }

    private void OnCaptureLost(object sender, PointerRoutedEventArgs e)
    {
        if (dragging && ReferenceEquals(sender, capture) && ReferenceEquals(e.OriginalSource, capture))
        {
            OnReleased(sender, e);
        }
    }

    private void OnReleased(object sender, PointerRoutedEventArgs e)
    {
        if (pointer is null || e.Pointer.PointerId != pointer.PointerId)
        {
            return;
        }
        pointer = null;
        if (!dragging)
        {
            return;
        }
        dragging = false;
        capture?.ReleasePointerCapture(e.Pointer);
        e.Handled = true;
        if (DragPose.Dismisses(offset, velocity, page.ActualHeight))
        {
            _ = App.MainWindow.Nav.PopAsync();
            return;
        }
        Motion.Spring((UIElement)page.Content, "Scale", Vector3.One, damping: 0.82f, periodMs: 60);
        Motion.Spring((UIElement)page.Content, "Translation", Vector3.Zero, damping: 0.82f, periodMs: 60);
        if (below is not null)
        {
            Motion.VisualOf(below).Opacity = 0.004f;
        }
    }

    private static bool StartsOnControl(DependencyObject? source)
    {
        for (var node = source; node is not null; node = Microsoft.UI.Xaml.Media.VisualTreeHelper.GetParent(node))
        {
            if (node is ButtonBase or TextBox or Selector or ScrollBar or RichTextBlock || node is TextBlock { IsTextSelectionEnabled: true })
            {
                return true;
            }
        }
        return false;
    }
}
