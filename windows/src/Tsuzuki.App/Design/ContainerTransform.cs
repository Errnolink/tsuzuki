using System.Numerics;
using Microsoft.UI;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.Presentation;
using Windows.Foundation;

namespace Tsuzuki.App.Design;

public sealed record HeroParts(IReadOnlyList<UIElement> Hero, IReadOnlyList<UIElement> Secondary);

public sealed record TransitionOrigin(FrameworkElement? Element, Rect? Snapshot)
{
    public static readonly TransitionOrigin None = new(null, null);

    public static TransitionOrigin From(FrameworkElement? element)
    {
        if (element is null || App.MainWindow?.Content is not UIElement root || !ContainerTransform.IsShown(element))
        {
            return new TransitionOrigin(element, null);
        }
        return new TransitionOrigin(element, ContainerTransform.BoundsIn(element, root));
    }

    public Box? SnapshotIn(UIElement relativeTo)
    {
        if (Snapshot is not { } rect || App.MainWindow?.Content is not UIElement root)
        {
            return null;
        }
        try
        {
            return ContainerTransform.ToBox(root.TransformToVisual(relativeTo).TransformBounds(rect));
        }
        catch (ArgumentException)
        {
            return null;
        }
    }
}

public static class ContainerTransform
{
    private const double ScrimTint = 0.35;
    private const float DefaultRadius = 8;

    public static Rect BoundsIn(FrameworkElement element, UIElement relativeTo)
    {
        var transform = element.TransformToVisual(relativeTo);
        return transform.TransformBounds(new Rect(0, 0, element.ActualWidth, element.ActualHeight));
    }

    public static Box ToBox(Rect rect) => new(rect.X, rect.Y, rect.Width, rect.Height);

    public static Rect ToRect(Box box) => new(box.X, box.Y, box.Width, box.Height);

    public static Box Viewport(FrameworkElement viewport) => new(0, 0, viewport.ActualWidth, viewport.ActualHeight);

    public static bool IsShown(FrameworkElement element)
    {
        if (element.XamlRoot is null || !element.IsLoaded || element.ActualWidth <= 0 || element.ActualHeight <= 0)
        {
            return false;
        }
        DependencyObject? node = element;
        while (node is not null)
        {
            if (node is UIElement { Visibility: Visibility.Collapsed })
            {
                return false;
            }
            node = VisualTreeHelper.GetParent(node);
        }
        return true;
    }

    public static Box? BoxIn(FrameworkElement? element, UIElement relativeTo)
    {
        if (element is null || !IsShown(element))
        {
            return null;
        }
        try
        {
            return ToBox(BoundsIn(element, relativeTo));
        }
        catch (ArgumentException)
        {
            return null;
        }
    }

    public static OpenPlan PlanOpen(TransitionOrigin origin, FrameworkElement viewport) =>
        TransitionPlanner.Open(Motion.Reduced, Viewport(viewport), BoxIn(origin.Element, viewport), origin.SnapshotIn(viewport));

    public static ReturnPlan PlanReturn(IReadOnlyList<FrameworkElement> hostCovers, TransitionOrigin origin, FrameworkElement viewport)
    {
        var boxes = new List<Box>(hostCovers.Count);
        var originCover = -1;
        foreach (var cover in hostCovers)
        {
            if (ReferenceEquals(cover, origin.Element))
            {
                originCover = boxes.Count;
            }
            boxes.Add(BoxIn(cover, viewport) ?? default);
        }
        return TransitionPlanner.Return(Motion.Reduced, Viewport(viewport), boxes, originCover, BoxIn(origin.Element, viewport), origin.SnapshotIn(viewport));
    }

    public static float RadiusOf(FrameworkElement? element)
    {
        var corners = element switch
        {
            Border border => border.CornerRadius,
            Control control => control.CornerRadius,
            Grid grid => grid.CornerRadius,
            StackPanel panel => panel.CornerRadius,
            _ => default,
        };
        return corners.TopLeft > 0 ? (float)corners.TopLeft : DefaultRadius;
    }

    public static Task Expand(UserControl page, Rect anchor, Rect cover, float radius, HeroParts parts, CancellationToken interrupt = default) =>
        Fly(page, anchor, cover, radius, Motion.HeroOpen, parts, opening: true, interrupt);

    public static Task Collapse(UserControl page, Rect anchor, Rect cover, float radius, HeroParts parts, CancellationToken interrupt = default) =>
        Fly(page, anchor, cover, radius, Motion.HeroBack, parts, opening: false, interrupt);

    public static async Task Fade(UIElement page, bool opening, CancellationToken interrupt = default)
    {
        var v = Motion.VisualOf(page);
        var c = v.Compositor;
        var duration = Motion.Reduced ? Motion.Short3 : Motion.Short4;
        var from = opening ? 0f : 1f;
        var to = opening ? 1f : 0f;
        v.Opacity = from;
        DevHarness.Mark(opening ? "fade in" : "fade out");
        var run = Motion.Run(c, _ => v.StartAnimation("Opacity", Motion.Scalar(c, from, to, duration, opening ? Curve.StandardDecelerate : Curve.StandardAccelerate)));
        await Motion.Interrupt(run, interrupt, () => v.StopAnimation("Opacity"));
        v.StopAnimation("Opacity");
        v.Opacity = to;
    }

    private static async Task Fly(UserControl page, Rect anchor, Rect cover, float radius, TimeSpan duration, HeroParts parts, bool opening, CancellationToken interrupt)
    {
        var visual = Motion.VisualOf(page);
        var c = visual.Compositor;
        var clipped = page.Content is UIElement content ? Motion.VisualOf(content) : visual;
        var flight = new Flight(c, page, anchor, cover, radius);
        var scrim = AddScrim(page);
        var scrimVisual = scrim is null ? null : Motion.VisualOf(scrim);
        flight.Progress.InsertScalar("p", opening ? 0f : 1f);
        visual.CenterPoint = Vector3.Zero;
        var clip = c.CreateRoundedRectangleGeometry();
        clipped.Clip = c.CreateGeometricClip(clip);
        visual.StartAnimation("Scale", flight.Expression("Vector3(Lerp(s0, 1, P.p), Lerp(s0, 1, P.p), 1)"));
        visual.StartAnimation("Translation", flight.Expression("Vector3(Lerp(tx, sx, P.p) - Lerp(s0, 1, P.p) * sx, Lerp(ty, sy, P.p) - Lerp(s0, 1, P.p) * sy, 0)"));
        clip.StartAnimation("Offset", flight.Expression("Vector2(Lerp(cx, 0, P.p), Lerp(cy, 0, P.p))"));
        clip.StartAnimation("Size", flight.Expression("Vector2(Lerp(cw, fw, P.p), Lerp(ch, fh, P.p))"));
        clip.StartAnimation("CornerRadius", flight.Expression("Vector2(r, r) * ((1 - Clamp(P.p / 0.8, 0, 1)) / Lerp(s0, 1, P.p))"));
        if (!opening && clipped != visual && (clipped.Scale.X < 0.999f || clipped.Properties.TryGetVector3("Translation", out var lift) == CompositionGetValueStatus.Succeeded && lift.Y > 0.5f))
        {
            clipped.Properties.TryGetVector3("Translation", out var dragLift);
            var dragScale = clipped.Scale.X;
            var blend = "Clamp((P.p - 0.35) / 0.65, 0, 1)";
            var scale = flight.Expression($"Vector3(Lerp(1, ds, {blend}), Lerp(1, ds, {blend}), 1)");
            scale.SetScalarParameter("ds", dragScale);
            var move = flight.Expression($"Vector3(0, dy * {blend}, 0)");
            move.SetScalarParameter("dy", dragLift.Y);
            clipped.StartAnimation("Scale", scale);
            clipped.StartAnimation("Translation", move);
        }
        var heroFade = opening ? "Clamp(P.p / 0.25, 0, 1)" : "Clamp((P.p - 0.7) / 0.2, 0, 1)";
        var secondaryFade = opening ? "Clamp((P.p - 0.05) / 0.28, 0, 1)" : "Clamp((P.p - 0.7) / 0.2, 0, 1)";
        foreach (var element in parts.Hero)
        {
            Motion.VisualOf(element).StartAnimation("Opacity", flight.Expression(heroFade));
        }
        foreach (var element in parts.Secondary)
        {
            Motion.VisualOf(element).StartAnimation("Opacity", flight.Expression(secondaryFade));
        }
        scrimVisual?.StartAnimation("Opacity", flight.Expression(opening ? "Clamp(P.p / 0.2, 0, 1)" : "Clamp((P.p - 0.07) / 0.93, 0, 1)"));
        visual.Opacity = 1;
        DevHarness.Mark(opening ? "expand pose" : "collapse pose");
        await Motion.FramesPresented(1, 100);
        DevHarness.Mark(opening ? "expand motion" : "collapse motion");
        var target = opening ? 1f : 0f;
        var run = Motion.Run(c, _ => flight.Progress.StartAnimation("p", Motion.CriticalSpring(c, 1 - target, target, duration, opening ? Motion.SnappySpring : Motion.SettlingSpring)));
        await Motion.Interrupt(run, interrupt, () => flight.Progress.StopAnimation("p"));
        flight.Progress.StopAnimation("p");
        flight.Progress.InsertScalar("p", target);
        if (opening)
        {
            visual.StopAnimation("Scale");
            visual.StopAnimation("Translation");
            visual.Scale = Vector3.One;
            visual.Properties.InsertVector3("Translation", Vector3.Zero);
            clipped.Clip = null;
            foreach (var element in parts.Hero.Concat(parts.Secondary))
            {
                var v = Motion.VisualOf(element);
                v.StopAnimation("Opacity");
                v.Opacity = 1;
            }
        }
        else
        {
            visual.Opacity = 0;
        }
        if (scrim?.Parent is Panel host)
        {
            host.Children.Remove(scrim);
        }
        DevHarness.Mark(opening ? "expand done" : "collapse done");
    }

    private static Rectangle? AddScrim(FrameworkElement page)
    {
        if (page.Parent is not Panel host)
        {
            return null;
        }
        var scrim = new Rectangle
        {
            Fill = new AcrylicBrush { TintColor = Colors.Black, TintOpacity = ScrimTint, TintLuminosityOpacity = ScrimTint, FallbackColor = Windows.UI.Color.FromArgb(102, 0, 0, 0) },
            IsHitTestVisible = false,
        };
        Motion.VisualOf(scrim).Opacity = 0;
        host.Children.Insert(host.Children.IndexOf(page), scrim);
        return scrim;
    }

    private sealed class Flight
    {
        private readonly Compositor compositor;
        private readonly float s0, tx, ty, sx, sy, cx, cy, cw, ch, fw, fh, r;

        public Flight(Compositor compositor, FrameworkElement page, Rect anchor, Rect cover, float radius)
        {
            this.compositor = compositor;
            Progress = compositor.CreatePropertySet();
            var geometry = new HeroFlight(ToBox(anchor), ToBox(cover), page.ActualWidth, page.ActualHeight);
            s0 = (float)geometry.StartScale;
            tx = (float)(anchor.X + anchor.Width / 2);
            ty = (float)(anchor.Y + anchor.Height / 2);
            sx = (float)(cover.X + cover.Width / 2);
            sy = (float)(cover.Y + cover.Height / 2);
            cx = (float)geometry.StartCard.X;
            cy = (float)geometry.StartCard.Y;
            cw = (float)geometry.StartCard.Width;
            ch = (float)geometry.StartCard.Height;
            fw = (float)page.ActualWidth;
            fh = (float)page.ActualHeight;
            r = radius;
        }

        public CompositionPropertySet Progress { get; }

        public ExpressionAnimation Expression(string text)
        {
            var e = compositor.CreateExpressionAnimation(text);
            e.SetReferenceParameter("P", Progress);
            e.SetScalarParameter("s0", s0);
            e.SetScalarParameter("tx", tx);
            e.SetScalarParameter("ty", ty);
            e.SetScalarParameter("sx", sx);
            e.SetScalarParameter("sy", sy);
            e.SetScalarParameter("cx", cx);
            e.SetScalarParameter("cy", cy);
            e.SetScalarParameter("cw", cw);
            e.SetScalarParameter("ch", ch);
            e.SetScalarParameter("fw", fw);
            e.SetScalarParameter("fh", fh);
            e.SetScalarParameter("r", r);
            return e;
        }
    }
}
