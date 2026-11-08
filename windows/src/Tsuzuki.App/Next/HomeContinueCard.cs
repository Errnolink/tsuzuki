using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class HomeContinueCard : Button
{
    private readonly CoverImage cover = new() { DecodeWidth = 96 };
    private readonly Border coverFrame;
    private readonly TextBlock title = ShellTheme.Text(string.Empty, "BodyStrongTextBlockStyle");
    private readonly TextBlock chapter = ShellTheme.Text(string.Empty, "CaptionTextBlockStyle");
    private readonly TextBlock when = ShellTheme.Text(string.Empty, "CaptionTextBlockStyle");
    private readonly ProgressBar progress = new() { Maximum = 100, Margin = new Thickness(0, 10, 0, 0) };

    public HomeContinueCard()
    {
        Width = 320;
        Height = 116;
        Padding = new Thickness(8);
        CornerRadius = new CornerRadius(8);
        HorizontalContentAlignment = HorizontalAlignment.Stretch;
        VerticalContentAlignment = VerticalAlignment.Stretch;
        BorderThickness = new Thickness(1);
        Resources["ButtonBackground"] = ShellTheme.Brush("CardBackgroundFillColorDefaultBrush");
        Resources["ButtonBackgroundPointerOver"] = ShellTheme.Brush("CardBackgroundFillColorSecondaryBrush");
        Resources["ButtonBackgroundPressed"] = ShellTheme.Brush("CardBackgroundFillColorSecondaryBrush");
        Resources["ButtonBorderBrush"] = ShellTheme.Brush("CardStrokeColorDefaultBrush");
        Resources["ButtonBorderBrushPointerOver"] = ShellTheme.Brush("CardStrokeColorDefaultBrush");
        Resources["ButtonBorderBrushPressed"] = ShellTheme.Brush("CardStrokeColorDefaultBrush");
        Background = ShellTheme.Brush("CardBackgroundFillColorDefaultBrush");
        BorderBrush = ShellTheme.Brush("CardStrokeColorDefaultBrush");

        cover.Stretch = Stretch.UniformToFill;
        coverFrame = new Border { Width = 64, Height = 96, CornerRadius = new CornerRadius(4), Child = cover, VerticalAlignment = VerticalAlignment.Center };
        title.TextWrapping = TextWrapping.Wrap;
        title.MaxLines = 2;
        chapter.Foreground = ShellTheme.Brush("TextFillColorSecondaryBrush");
        chapter.Margin = new Thickness(0, 2, 0, 0);
        when.Foreground = ShellTheme.Brush("TextFillColorTertiaryBrush");
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(14, 0, 8, 0) };
        text.Children.Add(title);
        text.Children.Add(chapter);
        text.Children.Add(when);
        text.Children.Add(progress);
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        Grid.SetColumn(text, 1);
        grid.Children.Add(coverFrame);
        grid.Children.Add(text);
        Content = grid;

        Click += (_, _) => _ = Log.Guard("home-continue", ContinueAsync);
        var menu = new MenuFlyout();
        var resume = new MenuFlyoutItem { Text = "Continue", Icon = ShellTheme.Glyph("\uE768") };
        resume.Click += (_, _) => _ = Log.Guard("home-continue", ContinueAsync);
        var details = new MenuFlyoutItem { Text = "Open details", Icon = ShellTheme.Glyph("\uE946") };
        details.Click += (_, _) => _ = Log.Guard("home-details", () => Entry is { } e ? AppNav.OpenDetailsAsync(e.MangaId, (FrameworkElement?)coverFrame) : Task.CompletedTask);
        menu.Items.Add(resume);
        menu.Items.Add(details);
        ContextFlyout = menu;
    }

    public ContinueEntry? Entry { get; private set; }

    public FrameworkElement CoverElement => coverFrame;

    public void Bind(ContinueEntry entry)
    {
        Entry = entry;
        cover.SourceId = entry.Source;
        cover.Url = entry.ThumbnailUrl;
        title.Text = entry.Title;
        ToolTipService.SetToolTip(title, entry.Title);
        var label = HomeHero.ChapterLine(entry);
        chapter.Text = label;
        when.Text = string.IsNullOrEmpty(entry.LastDevice)
            ? Design.Format.Relative(entry.LastReadAt)
            : $"{Design.Format.Relative(entry.LastReadAt)} on {entry.LastDevice}";
        var fraction = HomeHero.Fraction(entry);
        progress.Visibility = fraction is null ? Visibility.Collapsed : Visibility.Visible;
        progress.Value = (fraction ?? 0) * 100;
        AutomationProperties.SetName(this, $"Continue {entry.Title}, {label}");
    }

    private Task ContinueAsync() =>
        Entry is { } e ? AppNav.OpenReaderAsync(e.MangaId, e.ChapterId, Math.Max(e.LastPageRead, 0), coverFrame) : Task.CompletedTask;
}
