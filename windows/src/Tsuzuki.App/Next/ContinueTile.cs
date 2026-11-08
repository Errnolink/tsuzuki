using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class ContinueTile : Button
{
    private static Style? tileStyle;

    private readonly CoverImage cover = new() { DecodeWidth = 168 };
    private readonly Border coverFrame;
    private readonly AspectBox aspect;
    private readonly Button chapterButton;
    private readonly bool lead;
    private readonly TextBlock chapterLabel = new() { FontSize = 12, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold };
    private readonly FontIcon chapterIcon = new() { Glyph = "\uE768", FontSize = 11, FontFamily = (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"] };
    private readonly TextBlock title = new()
    {
        Style = StyleOf("BodyStrongTextBlockStyle"),
        TextTrimming = TextTrimming.CharacterEllipsis,
        TextWrapping = TextWrapping.Wrap,
        MaxLines = 2,
        Height = 40,
        VerticalAlignment = VerticalAlignment.Top,
        Margin = new Thickness(0, 8, 0, 0),
    };
    private readonly TextBlock sub = new()
    {
        Style = StyleOf("CaptionTextBlockStyle"),
        Foreground = Brush("TextFillColorSecondaryBrush"),
        TextTrimming = TextTrimming.CharacterEllipsis,
        MaxLines = 1,
        Margin = new Thickness(0, 2, 0, 0),
    };
    private readonly Grid progress;

    public ContinueTile(bool lead)
    {
        this.lead = lead;
        Style = tileStyle ??= (Style)Microsoft.UI.Xaml.Markup.XamlReader.Load(
            """
            <Style xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" TargetType="Button">
              <Setter Property="Background" Value="Transparent" />
              <Setter Property="BorderThickness" Value="0" />
              <Setter Property="Padding" Value="0" />
              <Setter Property="HorizontalAlignment" Value="Left" />
              <Setter Property="HorizontalContentAlignment" Value="Left" />
              <Setter Property="VerticalContentAlignment" Value="Top" />
              <Setter Property="Template">
                <Setter.Value>
                  <ControlTemplate TargetType="Button">
                    <ContentPresenter Content="{TemplateBinding Content}" />
                  </ControlTemplate>
                </Setter.Value>
              </Setter>
            </Style>
            """);

        coverFrame = new Border
        {
            CornerRadius = new CornerRadius(8),
            BorderBrush = Brush("CardStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            Background = Brush("CardBackgroundFillColorDefaultBrush"),
        };
        aspect = new AspectBox { Ratio = 1.5 };
        aspect.Children.Add(cover);

        chapterButton = new Button
        {
            Content = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, Children = { chapterIcon, chapterLabel } },
            Padding = new Thickness(8, 3, 8, 5),
            CornerRadius = new CornerRadius(12),
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(10),
            Style = StyleOf("AccentButtonStyle"),
            Visibility = lead ? Visibility.Visible : Visibility.Collapsed,
        };
        chapterLabel.Foreground = Brush("TextOnAccentFillColorPrimaryBrush");
        var coverGrid = new Grid();
        coverGrid.Children.Add(aspect);
        coverGrid.Children.Add(chapterButton);

        progress = new Grid
        {
            Height = 3,
            CornerRadius = new CornerRadius(1.5),
            Background = Brush("TzChipCount"),
            Margin = new Thickness(0, 7, 0, 0),
            Opacity = 0,
            ColumnDefinitions = { new ColumnDefinition(), new ColumnDefinition() },
        };
        progress.Children.Add(new Border { Background = Brush("AccentFillColorDefaultBrush"), CornerRadius = new CornerRadius(1.5, 0, 0, 1.5) });

        var text = new StackPanel();
        text.Children.Add(title);
        text.Children.Add(sub);
        text.Children.Add(progress);

        coverFrame.Child = coverGrid;
        var root = new StackPanel();
        root.Children.Add(coverFrame);
        root.Children.Add(text);
        Content = root;

        Width = 124;
        coverFrame.Width = 124;
        VerticalAlignment = VerticalAlignment.Bottom;
        cover.ImageLoaded += (_, _) => ApplyRatio();

        Click += (_, e) =>
        {
            if (!ReferenceEquals(e.OriginalSource, chapterButton))
            {
                Open();
            }
        };
        chapterButton.Click += (_, _) => Open();

        var menu = new MenuFlyout();
        var resume = new MenuFlyoutItem { Text = "Continue", Icon = new FontIcon { Glyph = "\uE768", FontFamily = LibraryActions.Symbols } };
        resume.Click += (_, _) => Open();
        var details = new MenuFlyoutItem { Text = "Open details", Icon = new FontIcon { Glyph = "\uE946", FontFamily = LibraryActions.Symbols } };
        details.Click += (_, _) => _ = Log.Guard("feed-shelf-details", () => Entry is { } entry ? AppNav.OpenDetailsAsync(entry.MangaId, coverFrame) : Task.CompletedTask);
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
        if (cover.Url != entry.ThumbnailUrl)
        {
            cover.Url = entry.ThumbnailUrl;
        }
        else if (entry.ThumbnailUrl is not null && !cover.HasSource)
        {
            cover.Reload();
        }
        ApplyRatio();
        title.Text = entry.Title;
        ToolTipService.SetToolTip(title, entry.Title);
        var chapter = entry.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(entry.ChapterNumber) : entry.ChapterName;
        chapterLabel.Text = chapter;
        sub.Text = lead
            ? entry.PageCount is > 0 ? $"Page {entry.LastPageRead + 1} of {entry.PageCount}" : $"Page {entry.LastPageRead + 1}"
            : chapter;
        if (HomeHero.Fraction(entry) is { } fraction)
        {
            progress.ColumnDefinitions[0].Width = new GridLength(fraction, GridUnitType.Star);
            progress.ColumnDefinitions[1].Width = new GridLength(1 - fraction, GridUnitType.Star);
            progress.Opacity = 1;
        }
        else
        {
            progress.Opacity = 0;
        }
        var page = entry.PageCount is > 0 ? $"page {entry.LastPageRead + 1} of {entry.PageCount}" : $"page {entry.LastPageRead + 1}";
        AutomationProperties.SetName(this, $"Continue {entry.Title}, {chapter}, {page}");
        AutomationProperties.SetName(chapterButton, $"Resume {entry.Title} at {chapter}");
    }

    private void ApplyRatio()
    {
        var ratio = CoverImage.RatioOf(cover.Url) is { } widthOverHeight and > 0 ? 1 / widthOverHeight : 1.5;
        if (Math.Abs(aspect.Ratio - ratio) > 0.001)
        {
            aspect.Ratio = ratio;
            aspect.InvalidateMeasure();
        }
    }

    private void Open() =>
        _ = Log.Guard("feed-shelf-open", () => Entry is { } entry
            ? AppNav.OpenReaderAsync(entry.MangaId, entry.ChapterId, Math.Max(entry.LastPageRead, 0), coverFrame)
            : Task.CompletedTask);

    private static Style StyleOf(string key) => (Style)Application.Current.Resources[key];

    private static Brush Brush(string key) => (Brush)Application.Current.Resources[key];
}
