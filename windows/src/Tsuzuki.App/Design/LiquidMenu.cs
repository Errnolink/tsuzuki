using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;

namespace Tsuzuki.App.Design;

public sealed record LiquidMenuItem(string Glyph, string Label, Action Invoke, bool Dangerous = false);

public sealed class LiquidMenu
{
    private static readonly TimeSpan Grow = TimeSpan.FromMilliseconds(300);
    private static readonly TimeSpan Shrink = TimeSpan.FromMilliseconds(220);

    private LiquidMenu()
    {
    }

    public static void Show(FrameworkElement anchor, IReadOnlyList<LiquidMenuItem> items)
    {
        if (anchor.XamlRoot is not { } root)
        {
            return;
        }
        var anchorRect = ContainerTransform.BoundsIn(anchor, root.Content);
        Border menu = BuildMenu();
        menu.MaxWidth = 300;
        Action close = () => { };
        RebuildRows(menu, items, () => close());
        menu.Measure(new Windows.Foundation.Size(root.Size.Width - 16, root.Size.Height - 16));
        var size = menu.DesiredSize;
        var left = Math.Max(8, Math.Min(anchorRect.Right - size.Width, root.Size.Width - size.Width - 8));
        var top = Math.Min(anchorRect.Bottom + 8, Math.Max(8, root.Size.Height - size.Height - 8));
        menu.Margin = new Thickness(left, top, 0, 0);

        Button scrim = new()
        {
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            HorizontalAlignment = HorizontalAlignment.Stretch,
            VerticalAlignment = VerticalAlignment.Stretch,
            BorderThickness = new Thickness(0),
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(0),
        };
        Grid host = new() { Width = root.Size.Width, Height = root.Size.Height };
        host.Children.Add(scrim);
        host.Children.Add(menu);

        var popup = new Popup { Child = host, XamlRoot = root };
        var session = new LiquidSession(popup, menu, anchorRect, new Windows.Foundation.Rect(left, top, size.Width, size.Height));
        close = session.Close;
        popup.Closed += (_, _) => session.OnClosed();
        scrim.Click += (_, _) => session.Close();
        host.KeyDown += (_, e) =>
        {
            if (e.Key == Windows.System.VirtualKey.Escape)
            {
                e.Handled = true;
                session.Close();
            }
        };
        popup.IsOpen = true;
        scrim.Focus(FocusState.Programmatic);
        session.Open();
    }

    private static void RebuildRows(Border menu, IReadOnlyList<LiquidMenuItem> items, Action close)
    {
        var list = (StackPanel)menu.Child;
        list.Children.Clear();
        foreach (var item in items)
        {
            Button row = new()
            {
                Style = (Style)Application.Current.Resources["TzGhostButton"],
                Height = 38,
                MinHeight = 0,
                Padding = new Thickness(12, 0, 16, 0),
                HorizontalAlignment = HorizontalAlignment.Stretch,
                CornerRadius = (CornerRadius)Application.Current.Resources["TzControlRadius"],
                Foreground = Design.Theme.Brush(item.Dangerous ? "TzDanger" : "TzText"),
            };
            StackPanel content = new() { Orientation = Orientation.Horizontal, Spacing = 12 };
            content.Children.Add(new FontIcon { Glyph = item.Glyph, FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"], FontSize = 16 });
            content.Children.Add(new TextBlock { Text = item.Label, FontSize = 14, VerticalAlignment = VerticalAlignment.Center });
            row.Content = content;
            var pick = item.Invoke;
            row.Click += (_, _) =>
            {
                close();
                pick();
            };
            list.Children.Add(row);
        }
    }

    private static Border BuildMenu()
    {
        Border menu = new()
        {
            CornerRadius = (CornerRadius)Application.Current.Resources["TzCardRadius"],
            BorderBrush = Design.Theme.Brush("TzStrokeStrong"),
            BorderThickness = new Thickness(1),
            Background = MakeGlass(),
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Top,
            Child = new StackPanel { Margin = new Thickness(6) },
        };
        return menu;
    }

    private static AcrylicBrush MakeGlass() => new()
    {
        TintColor = Design.Theme.Color("TzElevated"),
        TintOpacity = 0.78,
        FallbackColor = Design.Theme.Color("TzElevated"),
    };

    private sealed class LiquidSession(Popup popup, Border menu, Windows.Foundation.Rect anchor, Windows.Foundation.Rect target)
    {
        private bool closing;

        public void Open()
        {
            if (Motion.Reduced)
            {
                return;
            }
            var visual = Motion.VisualOf(menu);
            var c = visual.Compositor;
            var scale = new Vector2((float)(anchor.Width / target.Width), (float)(anchor.Height / target.Height));
            var origin = new Vector2((float)(anchor.X - target.X), (float)(anchor.Y - target.Y));
            var radius = new Vector2(17f / MathF.Sqrt(scale.X * scale.Y));
            visual.CenterPoint = Vector3.Zero;
            var geometry = c.CreateRoundedRectangleGeometry();
            geometry.Size = new Vector2((float)target.Width, (float)target.Height);
            geometry.CornerRadius = radius;
            visual.Clip = c.CreateGeometricClip(geometry);
            visual.Scale = new Vector3(scale.X, scale.Y, 1);
            visual.Properties.InsertVector3("Translation", new(origin.X, origin.Y, 0));
            var scaleSpring = c.CreateSpringVector3Animation();
            scaleSpring.FinalValue = Vector3.One;
            scaleSpring.DampingRatio = 0.72f;
            scaleSpring.Period = TimeSpan.FromMilliseconds(48);
            visual.StartAnimation("Scale", scaleSpring);
            var translationSpring = c.CreateSpringVector3Animation();
            translationSpring.FinalValue = Vector3.Zero;
            translationSpring.DampingRatio = 0.72f;
            translationSpring.Period = TimeSpan.FromMilliseconds(48);
            visual.StartAnimation("Translation", translationSpring);
            geometry.StartAnimation("CornerRadius", Motion.Vector2(c, radius, new Vector2((float)menu.CornerRadius.TopLeft), Grow, Curve.EmphasizedDecelerate));
            var content = Motion.VisualOf(menu.Child);
            content.CenterPoint = new((float)target.Width / 2, (float)target.Height / 2, 0);
            content.StartAnimation("Opacity", Motion.Scalar(c, 0.15f, 1, Grow, Curve.StandardDecelerate));
            content.StartAnimation("Scale", Motion.Vector3(c, new(1.1f, 1.1f, 1), Vector3.One, Grow, Curve.EmphasizedDecelerate));
        }

        public async void Close()
        {
            if (closing)
            {
                return;
            }
            closing = true;
            if (!Motion.Reduced)
            {
                var visual = Motion.VisualOf(menu);
                var c = visual.Compositor;
                var scale = new Vector3((float)(anchor.Width / target.Width), (float)(anchor.Height / target.Height), 1);
                var origin = new Vector3((float)(anchor.X - target.X), (float)(anchor.Y - target.Y), 0);
                await Motion.Run(c, _ =>
                {
                    visual.StartAnimation("Scale", Motion.Vector3(c, Vector3.One, scale, Shrink, Curve.EmphasizedAccelerate));
                    visual.StartAnimation("Translation", Motion.Vector3(c, Vector3.Zero, origin, Shrink, Curve.EmphasizedAccelerate));
                    visual.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Shrink, Curve.EmphasizedAccelerate));
                });
            }
            popup.IsOpen = false;
        }

        public void OnClosed()
        {
            var visual = Motion.VisualOf(menu);
            visual.StopAnimation("Scale");
            visual.StopAnimation("Translation");
            visual.StopAnimation("Opacity");
            visual.Scale = Vector3.One;
            visual.Opacity = 1;
            visual.Properties.InsertVector3("Translation", Vector3.Zero);
            visual.Clip = null;
        }
    }
}
