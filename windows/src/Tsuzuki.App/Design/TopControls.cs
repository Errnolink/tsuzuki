using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;

namespace Tsuzuki.App.Design;

public sealed record TopBarAction(string Glyph, string Label, Action<FrameworkElement> Invoke);

public sealed record TopBarState(string? Title, IReadOnlyList<TopBarAction> Actions)
{
    public static TopBarState Empty { get; } = new(null, Array.Empty<TopBarAction>());
}

public sealed class TopControls
{
    private static readonly TimeSpan SwapOut = TimeSpan.FromMilliseconds(100);
    private static readonly TimeSpan SwapIn = TimeSpan.FromMilliseconds(60);
    private static readonly TimeSpan Resize = TimeSpan.FromMilliseconds(200);

    private readonly Border capsule;
    private readonly StackPanel icons;
    private readonly TextBlock title;
    private readonly AutoSuggestBox search;
    private readonly List<Button> buttons = [];
    private readonly List<string> glyphs = [];
    private TopBarState state = TopBarState.Empty;
    private bool searchHot;
    private bool titleShown;
    private bool capsuleShown;

    public TopControls(Border capsule, StackPanel icons, TextBlock title, AutoSuggestBox search)
    {
        this.capsule = capsule;
        this.icons = icons;
        this.title = title;
        this.search = search;
        Retint();
        capsule.Opacity = 1;
        Motion.VisualOf(capsule).Opacity = 0;
        search.GotFocus += (_, _) => { searchHot = true; UpdateCenter(); };
        search.LostFocus += (_, _) => { searchHot = search.Text.Length > 0; UpdateCenter(); };
        search.TextChanged += (_, _) => searchHot = search.FocusState != FocusState.Unfocused || search.Text.Length > 0;
    }

    public void Retint()
    {
        if (capsule.Background is AcrylicBrush acrylic)
        {
            var tint = Theme.Color("TzCard");
            acrylic.TintColor = tint;
            acrylic.FallbackColor = tint;
        }
    }

    public void RevealSearch()
    {
        searchHot = true;
        UpdateCenter();
        search.Focus(FocusState.Keyboard);
    }

    public void Present(TopBarState next)
    {
        state = next;
        SyncCapsule(next.Actions);
        UpdateCenter();
    }

    private void UpdateCenter()
    {
        var wantTitle = state.Title is { Length: > 0 } && !searchHot;
        if (wantTitle == titleShown)
        {
            return;
        }
        titleShown = wantTitle;
        if (Motion.Reduced)
        {
            title.Visibility = wantTitle ? Visibility.Visible : Visibility.Collapsed;
            title.Opacity = 1;
            search.Visibility = wantTitle ? Visibility.Collapsed : Visibility.Visible;
            search.Opacity = 1;
            return;
        }
        if (wantTitle)
        {
            _ = SwapOutElement(search).ContinueWith(_ => Dispatcher(() =>
            {
                search.Visibility = Visibility.Collapsed;
                title.Text = state.Title;
                title.Visibility = Visibility.Visible;
                _ = SwapInElement(title);
            }), TaskScheduler.Default);
        }
        else
        {
            _ = SwapOutElement(title).ContinueWith(_ => Dispatcher(() =>
            {
                title.Visibility = Visibility.Collapsed;
                search.Visibility = Visibility.Visible;
                _ = SwapInElement(search);
            }), TaskScheduler.Default);
        }
    }

    private void SyncCapsule(IReadOnlyList<TopBarAction> actions)
    {
        var changed = false;
        for (var i = 0; i < actions.Count || i < glyphs.Count; i++)
        {
            var glyph = i < actions.Count ? actions[i].Glyph : null;
            if (i < glyphs.Count && glyphs[i] == glyph)
            {
                buttons[i].Tag = actions[i];
                AutomationProperties.SetName(buttons[i], actions[i].Label);
                continue;
            }
            changed = true;
            if (glyph is null)
            {
                icons.Children.Remove(buttons[i]);
                buttons.RemoveAt(i);
                glyphs.RemoveAt(i);
                i--;
                continue;
            }
            if (i < buttons.Count)
            {
                buttons[i].Tag = actions[i];
                AutomationProperties.SetName(buttons[i], actions[i].Label);
                SwapIcon(buttons[i], glyph, i);
            }
            else
            {
                var button = MakeButton(actions[i], glyph);
                buttons.Add(button);
                glyphs.Add(glyph);
                icons.Children.Add(button);
                if (!Motion.Reduced)
                {
                    var visual = Motion.VisualOf(button);
                    visual.StartAnimation("Opacity", Motion.Scalar(visual.Compositor, 0, 1, SwapIn, Curve.StandardDecelerate));
                }
            }
        }
        if (!changed)
        {
            return;
        }
        var width = actions.Count == 0 ? 0d : 12 + actions.Count * 34 + (actions.Count - 1) * 2;
        var show = actions.Count > 0;
        var capsuleVisual = Motion.VisualOf(capsule);
        if (Motion.Reduced)
        {
            capsule.Width = width;
            capsuleVisual.Opacity = show ? 1 : 0;
            capsuleShown = show;
            return;
        }
        AnimateWidth(width);
        capsuleVisual.StartAnimation("Opacity", Motion.Scalar(capsuleVisual.Compositor, capsuleShown ? 1f : 0f, show ? 1f : 0f, Resize, Curve.StandardDecelerate));
        capsuleShown = show;
    }

    private Button MakeButton(TopBarAction action, string glyph)
    {
        Button button = new()
        {
            Style = (Style)Application.Current.Resources["TzIconButton"],
            Width = 34,
            Height = 34,
            CornerRadius = new CornerRadius(17),
            Content = new FontIcon { Glyph = glyph, FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"], FontSize = 16 },
            Tag = action,
        };
        AutomationProperties.SetName(button, action.Label);
        button.Click += (_, _) => (button.Tag as TopBarAction)?.Invoke(button);
        return button;
    }

    private void SwapIcon(Button button, string glyph, int index)
    {
        if (Motion.Reduced)
        {
            (button.Content as FontIcon)!.Glyph = glyph;
            glyphs[index] = glyph;
            return;
        }
        var icon = (FontIcon)button.Content;
        _ = SwapOutElement(icon).ContinueWith(_ => Dispatcher(() =>
        {
            if (buttons.Count <= index || !ReferenceEquals(buttons[index], button) || glyphs[index] == glyph)
            {
                return;
            }
            icon.Glyph = glyph;
            glyphs[index] = glyph;
            _ = SwapInElement(icon);
        }), TaskScheduler.Default);
    }

    private static Task SwapOutElement(UIElement element)
    {
        var v = Motion.VisualOf(element);
        var c = v.Compositor;
        v.CenterPoint = new((float)element.ActualSize.X / 2, (float)element.ActualSize.Y / 2, 0);
        return Motion.Run(c, _ =>
        {
            v.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, SwapOut, Curve.StandardAccelerate));
            v.StartAnimation("Scale", Motion.Vector3(c, Vector3.One, new(1.25f, 1.25f, 1), SwapOut, Curve.StandardAccelerate));
        });
    }

    private static Task SwapInElement(UIElement element)
    {
        var v = Motion.VisualOf(element);
        var c = v.Compositor;
        v.CenterPoint = new((float)element.ActualSize.X / 2, (float)element.ActualSize.Y / 2, 0);
        return Motion.Run(c, _ =>
        {
            v.StartAnimation("Opacity", Motion.Scalar(c, 0, 1, SwapIn, Curve.StandardDecelerate));
            v.StartAnimation("Scale", Motion.Vector3(c, new(0.85f, 0.85f, 1), Vector3.One, SwapIn, Curve.StandardDecelerate));
        });
    }

    private void AnimateWidth(double to)
    {
        var from = capsule.ActualWidth > 0 ? capsule.ActualWidth : capsule.Width;
        if (Math.Abs(from - to) < 0.5)
        {
            capsule.Width = to;
            return;
        }
        var animation = new DoubleAnimation
        {
            From = from,
            To = to,
            Duration = Resize,
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
            EnableDependentAnimation = true,
        };
        Storyboard.SetTarget(animation, capsule);
        Storyboard.SetTargetProperty(animation, "Width");
        var board = new Storyboard();
        board.Children.Add(animation);
        board.Begin();
    }

    private void Dispatcher(Action run) => capsule.DispatcherQueue.TryEnqueue(() => run());
}
