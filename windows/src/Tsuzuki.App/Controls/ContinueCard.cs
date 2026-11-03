using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Controls;

public sealed partial class ContinueCard : Button
{
    private readonly CoverImage cover;
    private readonly CoverImage ambient;
    private readonly Border surface;

    public ContinueCard(ContinueEntry entry, string? thisDeviceName)
    {
        Entry = entry;
        Style = (Style)Application.Current.Resources["TzRowButton"];
        CornerRadius = new CornerRadius(16);
        Width = 340;
        Height = 132;
        Padding = new Thickness(0);

        ambient = new CoverImage { DecodeWidth = 10, Url = entry.ThumbnailUrl, Opacity = 0.42 };
        var fade = new Border
        {
            Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0, 0),
                EndPoint = new Windows.Foundation.Point(1, 0),
                GradientStops =
                {
                    new GradientStop { Color = Windows.UI.Color.FromArgb(0, 0, 0, 0), Offset = 0 },
                    new GradientStop { Color = Windows.UI.Color.FromArgb(110, 0, 0, 0), Offset = 1 },
                },
            },
        };

        cover = new CoverImage { DecodeWidth = 120, Url = entry.ThumbnailUrl };
        var coverFrame = new Border
        {
            Width = 72,
            Height = 108,
            CornerRadius = new CornerRadius(8),
            BorderBrush = Design.Theme.Brush("TzStroke", "Dark"),
            BorderThickness = new Thickness(1),
            Child = cover,
            VerticalAlignment = VerticalAlignment.Center,
        };

        var title = new TextBlock
        {
            Text = entry.Title,
            Style = Res<Style>("TzBodyStrong"),
            FontSize = 15,
            LineHeight = 19,
            TextWrapping = TextWrapping.Wrap,
            MaxLines = 2,
            Foreground = new SolidColorBrush(Microsoft.UI.Colors.White),
        };
        var chapter = ChapterLabel(entry);
        var meta = new TextBlock
        {
            Text = chapter,
            Style = Res<Style>("TzCaption"),
            Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(210, 255, 255, 255)),
            Margin = new Thickness(0, 4, 0, 0),
        };

        Grid? track = null;
        if (entry.PageCount is > 0)
        {
            track = new Grid { Height = 4, Margin = new Thickness(0, 10, 0, 0), CornerRadius = new CornerRadius(2), Background = new SolidColorBrush(Windows.UI.Color.FromArgb(60, 255, 255, 255)) };
            var fill = new Border { Background = Design.Theme.Brush("TzAccent", "Dark"), CornerRadius = new CornerRadius(2) };
            var fraction = Math.Clamp((entry.LastPageRead + 1) / (double)entry.PageCount.Value, 0, 1);
            track.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(Math.Max(fraction, 0.001), GridUnitType.Star) });
            track.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(Math.Max(1 - fraction, 0.001), GridUnitType.Star) });
            track.Children.Add(fill);
        }

        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(14, 0, 14, 0) };
        text.Children.Add(title);
        text.Children.Add(meta);
        if (track != null)
        {
            text.Children.Add(track);
        }
        if (!string.IsNullOrEmpty(entry.LastDevice) && entry.LastDevice != thisDeviceName)
        {
            var from = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 5, Margin = new Thickness(0, 10, 0, 0) };
            from.Children.Add(new FontIcon { Glyph = Icons.Smartphone, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 12, Foreground = Design.Theme.Brush("TzAccent", "Dark") });
            from.Children.Add(new TextBlock { Text = "From " + entry.LastDevice, FontSize = 11, FontWeight = FontWeights.SemiBold, Foreground = Design.Theme.Brush("TzAccent", "Dark") });
            text.Children.Add(from);
        }

        var row = new Grid { Padding = new Thickness(14, 12, 4, 12) };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.Children.Add(coverFrame);
        Grid.SetColumn(text, 1);
        row.Children.Add(text);

        var layers = new Grid();
        layers.Children.Add(ambient);
        layers.Children.Add(fade);
        layers.Children.Add(row);
        surface = new Border
        {
            CornerRadius = new CornerRadius(16),
            Background = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 26, 27, 32)),
            BorderBrush = Design.Theme.Brush("TzStroke", "Dark"),
            BorderThickness = new Thickness(1),
            Child = layers,
            Shadow = new ThemeShadow(),
            TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromMilliseconds(200) },
        };
        ambient.Stretch = Stretch.UniformToFill;
        Content = surface;
        AutomationProperties.SetName(this, $"Continue {entry.Title}, {chapter}");
        PointerEntered += (_, _) => { if (!Motion.Reduced) surface.Translation = new Vector3(0, -3, 14); };
        PointerExited += (_, _) => surface.Translation = Vector3.Zero;
    }

    public ContinueEntry Entry { get; }

    public FrameworkElement CoverElement => cover;

    public static string ChapterLabel(ContinueEntry e)
    {
        var ch = e.ChapterNumber >= 0 ? "Ch. " + e.ChapterNumber.ToString("0.#") : e.ChapterName;
        if (e.LastPageRead <= 0)
        {
            return e.PageCount is > 0 ? ch + " · Start" : ch;
        }
        return e.PageCount is > 0 ? $"{ch} · Page {e.LastPageRead + 1} of {e.PageCount}" : $"{ch} · Page {e.LastPageRead + 1}";
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];
}
