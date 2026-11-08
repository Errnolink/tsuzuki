using System.Numerics;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;

namespace Tsuzuki.App;

public static class Toasts
{
    public static void Show(string title, string? body = null, string? glyph = null, string? actionLabel = null, Action<FrameworkElement>? action = null, string? coverUrl = null, int seconds = 5, string? key = null)
    {
        var host = App.MainWindow.Toasts;
        var res = Application.Current.Resources;
        var row = new Grid { ColumnSpacing = 14, Padding = new Thickness(14, 12, 12, 12) };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        FrameworkElement lead;
        if (coverUrl is not null)
        {
            lead = new Border
            {
                Width = 40,
                Height = 60,
                CornerRadius = new CornerRadius(6),
                Child = new CoverImage { DecodeWidth = 60, Url = coverUrl },
            };
        }
        else
        {
            lead = new Border
            {
                Width = 36,
                Height = 36,
                CornerRadius = new CornerRadius(18),
                Background = Design.Theme.Brush("TzAccentSoft"),
                Child = new FontIcon { Glyph = glyph ?? Icons.Info, FontFamily = (FontFamily)res["TzIconFont"], FontSize = 16, Foreground = Design.Theme.Brush("TzAccent") },
            };
        }
        lead.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(lead);
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 2 };
        text.Children.Add(new TextBlock { Text = title, Style = (Style)res["TzBodyStrong"], TextWrapping = TextWrapping.Wrap, MaxLines = 2 });
        if (body is not null)
        {
            text.Children.Add(new TextBlock { Text = body, Style = (Style)res["TzCaption"], TextWrapping = TextWrapping.Wrap, MaxLines = 3 });
        }
        Grid.SetColumn(text, 1);
        row.Children.Add(text);
        Border? card = null;
        if (actionLabel is not null && action is not null)
        {
            var button = new Button { Style = (Style)res["TzAccentButton"], Content = actionLabel, VerticalAlignment = VerticalAlignment.Center, Height = 32, MinHeight = 0, Padding = new Thickness(14, 0, 14, 0), FontSize = 13 };
            button.Click += (_, _) =>
            {
                action(lead);
                if (card is not null)
                {
                    _ = Dismiss(host, card);
                }
            };
            Grid.SetColumn(button, 2);
            row.Children.Add(button);
        }
        card = new Border
        {
            Width = 380,
            CornerRadius = (CornerRadius)Application.Current.Resources["TzCardRadius"],
            Background = Design.Theme.Brush("TzElevated"),
            BorderBrush = Design.Theme.Brush("TzStrokeStrong"),
            BorderThickness = new Thickness(1),
            Child = row,
            Shadow = new ThemeShadow(),
            Translation = new Vector3(0, 0, 32),
            Tag = key,
        };
        var replaced = key is null ? -1 : host.Children.ToList().FindIndex(c => c is Border { Tag: string tag } && tag == key);
        if (replaced >= 0)
        {
            host.Children[replaced] = card;
            card.Translation = new Vector3(0, 0, 32);
        }
        else
        {
            host.Children.Add(card);
        }
        while (host.Children.Count > 3)
        {
            host.Children.RemoveAt(0);
        }
        var v = Motion.VisualOf(card);
        var c = v.Compositor;
        v.StartAnimation("Opacity", Motion.Scalar(c, replaced >= 0 ? 0.6f : 0, 1, Motion.Short4, Curve.StandardDecelerate));
        if (!Motion.Reduced && replaced < 0)
        {
            v.StartAnimation("Translation", Motion.Vector3(c, new Vector3(36, 0, 32), new Vector3(0, 0, 32), Motion.Medium2, Curve.EmphasizedDecelerate));
        }
        var timer = card.DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromSeconds(seconds);
        timer.IsRepeating = false;
        timer.Tick += (_, _) => _ = Dismiss(host, card);
        card.PointerEntered += (_, _) => timer.Stop();
        card.PointerExited += (_, _) => timer.Start();
        timer.Start();
    }

    private static async Task Dismiss(Panel host, Border card)
    {
        if (!host.Children.Contains(card))
        {
            return;
        }
        var v = Motion.VisualOf(card);
        var c = v.Compositor;
        await Motion.Run(c, _ =>
        {
            v.StartAnimation("Opacity", Motion.Scalar(c, 1, 0, Motion.Short4, Curve.EmphasizedAccelerate));
            if (!Motion.Reduced)
            {
                v.StartAnimation("Translation", Motion.Vector3(c, new Vector3(0, 0, 32), new Vector3(36, 0, 32), Motion.Short4, Curve.EmphasizedAccelerate));
            }
        });
        host.Children.Remove(card);
    }
}
