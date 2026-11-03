using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;

namespace Tsuzuki.App.Design;

public static class ContainerTransform
{
    public static Rect BoundsIn(FrameworkElement element, UIElement relativeTo)
    {
        var transform = element.TransformToVisual(relativeTo);
        return transform.TransformBounds(new Rect(0, 0, element.ActualWidth, element.ActualHeight));
    }

    public static bool IsOnScreen(Rect rect, FrameworkElement viewport)
    {
        return rect.Width > 0 && rect.Bottom > 0 && rect.Top < viewport.ActualHeight && rect.Right > 0 && rect.Left < viewport.ActualWidth;
    }

    public static (Vector2 Scale, Vector2 Origin, Vector2 CardRadius, CompositionRoundedRectangleGeometry Geometry, Vector2 ClipOffset, Vector2 ClipSize) ApplyFrame1(FrameworkElement page, Rect anchor, Rect cover, float radius, IEnumerable<UIElement> fadeIn, FrameworkElement? below)
    {
        var visual = Motion.VisualOf(page);
        var c = visual.Compositor;
        var full = new Vector2((float)page.ActualWidth, (float)page.ActualHeight);
        var (scale, origin) = CardPlacement(anchor, cover);
        var cardRadius = new Vector2(radius / MathF.Sqrt(scale.X * scale.Y));
        var (clipOffset, clipSize) = CardClipStart(cover);
        visual.CenterPoint = Vector3.Zero;
        visual.Scale = new(scale.X, scale.Y, 1);
        visual.Properties.InsertVector3("Translation", new(origin.X, origin.Y, 0));
        var geometry = c.CreateRoundedRectangleGeometry();
        geometry.Offset = clipOffset;
        geometry.Size = clipSize;
        geometry.CornerRadius = cardRadius;
        visual.Clip = c.CreateGeometricClip(geometry);
        foreach (var element in fadeIn)
        {
            Motion.VisualOf(element).Opacity = 0.2f;
        }
        if (below is not null)
        {
            Motion.VisualOf(below).Opacity = 1f;
        }
        return (scale, origin, cardRadius, geometry, clipOffset, clipSize);
    }

    private static (Vector2 Offset, Vector2 Size) CardClipStart(Rect cover) =>
        (new((float)cover.X, (float)cover.Y), new((float)cover.Width, (float)cover.Height));

    public static async Task Expand(FrameworkElement page, Rect anchor, Rect cover, float radius, TimeSpan duration, IEnumerable<UIElement> fadeIn, FrameworkElement? below, CancellationToken interrupt = default)
    {
        var visual = Motion.VisualOf(page);
        var c = visual.Compositor;
        var (scale, origin, cardRadius, geometry, clipOffset, clipSize) = ApplyFrame1(page, anchor, cover, radius, fadeIn, below);
        var full = new Vector2((float)page.ActualWidth, (float)page.ActualHeight);
        var fadeDuration = TimeSpan.FromMilliseconds(duration.TotalMilliseconds * 0.4);
        await Motion.FramesPresented(3, 250);
        if (!interrupt.IsCancellationRequested)
        {
            var task = Motion.Run(c, _ =>
            {
                visual.StartAnimation("Scale", Motion.Vector3(c, new(scale.X, scale.Y, 1), Vector3.One, duration, Curve.EmphasizedDecelerate));
                visual.StartAnimation("Translation", Motion.Vector3(c, new(origin.X, origin.Y, 0), Vector3.Zero, duration, Curve.EmphasizedDecelerate));
                geometry.StartAnimation("CornerRadius", Motion.Vector2(c, cardRadius, Vector2.Zero, duration, Curve.EmphasizedDecelerate));
                geometry.StartAnimation("Offset", Motion.Vector2(c, clipOffset, Vector2.Zero, duration, Curve.EmphasizedDecelerate));
                geometry.StartAnimation("Size", Motion.Vector2(c, clipSize, full, duration, Curve.EmphasizedDecelerate));
                foreach (var element in fadeIn)
                {
                    Motion.VisualOf(element).StartAnimation("Opacity", Motion.Scalar(c, 0.2f, 1, fadeDuration, Curve.StandardDecelerate));
                }
                if (below is not null)
                {
                    Motion.VisualOf(below).StartAnimation("Opacity", Motion.Scalar(c, 1, 0.35f, duration, Curve.EmphasizedDecelerate));
                }
            });
            await Motion.Interrupt(task, interrupt, () => SnapOpen(visual, fadeIn, below));
        }
        SnapOpen(visual, fadeIn, below);
    }

    public static async Task Collapse(FrameworkElement page, Rect anchor, Rect cover, float radius, TimeSpan duration, IEnumerable<UIElement> fadeOut, FrameworkElement? below, CancellationToken interrupt = default)
    {
        var visual = Motion.VisualOf(page);
        var c = visual.Compositor;
        var full = new Vector2((float)page.ActualWidth, (float)page.ActualHeight);
        var (scale, origin) = CardPlacement(anchor, cover);
        var cardRadius = new Vector2(radius / MathF.Sqrt(scale.X * scale.Y));
        var (clipOffset, clipSize) = CardClipStart(cover);
        visual.CenterPoint = Vector3.Zero;
        var geometry = c.CreateRoundedRectangleGeometry();
        geometry.Size = full;
        geometry.CornerRadius = Vector2.Zero;
        visual.Clip = c.CreateGeometricClip(geometry);
        if (below is not null)
        {
            Motion.VisualOf(below).Opacity = 0.35f;
        }
        var fadeDuration = TimeSpan.FromMilliseconds(duration.TotalMilliseconds * 0.5);
        await Motion.FramesPresented(3, 250);
        if (interrupt.IsCancellationRequested)
        {
            SnapShut(visual, scale, origin, fadeOut, below);
            return;
        }
        var task = Motion.Run(c, _ =>
        {
            visual.StartAnimation("Scale", Motion.Vector3(c, Vector3.One, new(scale.X, scale.Y, 1), duration, Curve.EmphasizedDecelerate));
            visual.StartAnimation("Translation", Motion.Vector3(c, Vector3.Zero, new(origin.X, origin.Y, 0), duration, Curve.EmphasizedDecelerate));
            geometry.StartAnimation("CornerRadius", Motion.Vector2(c, Vector2.Zero, cardRadius, duration, Curve.EmphasizedDecelerate));
            geometry.StartAnimation("Offset", Motion.Vector2(c, Vector2.Zero, clipOffset, duration, Curve.EmphasizedDecelerate));
            geometry.StartAnimation("Size", Motion.Vector2(c, full, clipSize, duration, Curve.EmphasizedDecelerate));
            foreach (var element in fadeOut)
            {
                Motion.VisualOf(element).StartAnimation("Opacity", Motion.Scalar(c, 1, 0, fadeDuration, Curve.StandardAccelerate));
            }
            if (below is not null)
            {
                Motion.VisualOf(below).StartAnimation("Opacity", Motion.Scalar(c, 0.35f, 1, duration, Curve.EmphasizedDecelerate));
            }
        });
        await Motion.Interrupt(task, interrupt, () => SnapShut(visual, scale, origin, fadeOut, below));
    }

    private static (Vector2 Scale, Vector2 Origin) CardPlacement(Rect anchor, Rect cover) =>
        (new((float)(anchor.Width / cover.Width), (float)(anchor.Height / cover.Height)),
            new((float)(anchor.X - cover.X * anchor.Width / cover.Width), (float)(anchor.Y - cover.Y * anchor.Height / cover.Height)));

    private static void SnapOpen(Visual visual, IEnumerable<UIElement> fadeIn, FrameworkElement? below)
    {
        visual.StopAnimation("Scale");
        visual.StopAnimation("Translation");
        visual.Scale = Vector3.One;
        visual.Properties.InsertVector3("Translation", Vector3.Zero);
        visual.Clip = null;
        foreach (var element in fadeIn)
        {
            var v = Motion.VisualOf(element);
            v.StopAnimation("Opacity");
            v.Opacity = 1;
        }
        if (below is not null)
        {
            var bv = Motion.VisualOf(below);
            bv.StopAnimation("Opacity");
            bv.Opacity = 1;
        }
    }

    private static void SnapShut(Visual visual, Vector2 scale, Vector2 origin, IEnumerable<UIElement> fadeOut, FrameworkElement? below)
    {
        visual.StopAnimation("Scale");
        visual.StopAnimation("Translation");
        visual.Scale = new(scale.X, scale.Y, 1);
        visual.Properties.InsertVector3("Translation", new(origin.X, origin.Y, 0));
        visual.Clip = null;
        foreach (var element in fadeOut)
        {
            var v = Motion.VisualOf(element);
            v.StopAnimation("Opacity");
            v.Opacity = 0;
        }
        if (below is not null)
        {
            var bv = Motion.VisualOf(below);
            bv.StopAnimation("Opacity");
            bv.Opacity = 1;
        }
    }

}

