using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class LibraryListRow : Button
{
    private static Style? rowStyle;
    private readonly Border coverFrame;
    private readonly CoverImage cover;
    private readonly TextBlock title;
    private readonly TextBlock source;
    private readonly TextBlock unread;
    private readonly TextBlock lastRead;
    private readonly ProgressBar progress;
    private readonly TextBlock progressText;
    private readonly TextBlock next;
    private int version;
    private string nextKey = string.Empty;
    private bool selectionActive;

    public static void Columns(Grid grid)
    {
        foreach (var width in new[] { new GridLength(56), new GridLength(1, GridUnitType.Star), new GridLength(150), new GridLength(80), new GridLength(130), new GridLength(170), new GridLength(170) })
        {
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = width });
        }
        grid.ColumnSpacing = 12;
    }

    public LibraryListRow()
    {
        Style = rowStyle ??= (Style)Microsoft.UI.Xaml.Markup.XamlReader.Load(
            """
            <Style xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" TargetType="Button">
              <Setter Property="Background" Value="Transparent" />
              <Setter Property="Padding" Value="0" />
              <Setter Property="UseSystemFocusVisuals" Value="True" />
              <Setter Property="HorizontalAlignment" Value="Stretch" />
              <Setter Property="HorizontalContentAlignment" Value="Stretch" />
              <Setter Property="Template">
                <Setter.Value>
                  <ControlTemplate TargetType="Button">
                    <ContentPresenter Background="{TemplateBinding Background}" Content="{TemplateBinding Content}" HorizontalContentAlignment="Stretch" VerticalContentAlignment="Center" />
                  </ControlTemplate>
                </Setter.Value>
              </Setter>
            </Style>
            """);

        cover = new CoverImage { DecodeWidth = 96 };
        coverFrame = new Border
        {
            Width = 40,
            Height = 60,
            CornerRadius = new CornerRadius(4),
            BorderBrush = LibraryActions.Brush("CardStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            Child = cover,
            HorizontalAlignment = HorizontalAlignment.Left,
        };
        title = Text("BodyStrongTextBlockStyle", primary: true);
        source = Text("BodyTextBlockStyle", primary: false);
        unread = Text("BodyTextBlockStyle", primary: true);
        lastRead = Text("BodyTextBlockStyle", primary: false);
        next = Text("BodyTextBlockStyle", primary: false);
        progress = new ProgressBar { Minimum = 0, Maximum = 1, Width = 96, VerticalAlignment = VerticalAlignment.Center };
        progressText = Text("CaptionTextBlockStyle", primary: false);
        var progressCell = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        progressCell.Children.Add(progress);
        progressCell.Children.Add(progressText);

        var grid = new Grid { Padding = new Thickness(8, 6, 8, 6) };
        Columns(grid);
        Place(grid, coverFrame, 0);
        Place(grid, title, 1);
        Place(grid, source, 2);
        Place(grid, unread, 3);
        Place(grid, lastRead, 4);
        Place(grid, progressCell, 5);
        Place(grid, next, 6);
        Content = grid;

        Click += (_, _) =>
        {
            if (Data is not null)
            {
                Clicked?.Invoke(this, this);
            }
        };
        ContextRequested += (_, e) =>
        {
            e.Handled = true;
            if (Data is { } data)
            {
                LibraryActions.ShowFlyout(this, e, data.MangaId, data.Title, data.Tag as LibraryEntry, coverFrame);
            }
        };
    }

    public static readonly DependencyProperty DataProperty = DependencyProperty.Register(
        nameof(Data), typeof(TileData), typeof(LibraryListRow), new PropertyMetadata(null, (d, _) => ((LibraryListRow)d).Bind()));

    public TileData? Data
    {
        get => (TileData?)GetValue(DataProperty);
        set => SetValue(DataProperty, value);
    }

    public FrameworkElement CoverElement => coverFrame;

    public event EventHandler<LibraryListRow>? Clicked;

    public bool SelectionActive
    {
        get => selectionActive;
        set
        {
            selectionActive = value;
            IsHitTestVisible = !value;
            IsTabStop = !value;
        }
    }

    private void Bind()
    {
        if (Data is not { } data)
        {
            return;
        }
        cover.SourceId = data.Source;
        if (cover.Url != data.ThumbnailUrl)
        {
            cover.Url = data.ThumbnailUrl;
        }
        title.Text = data.Title;
        ToolTipService.SetToolTip(title, data.Title);
        var entry = data.Tag as LibraryEntry;
        source.Text = Services.Engine?.Sources.DisplayName(data.Source) ?? string.Empty;
        unread.Text = data.Badge > 0 ? data.Badge.ToString("N0") : "—";
        lastRead.Text = entry is { LastReadAt: > 0 } ? Format.Relative(entry.LastReadAt) : "Never";
        var (read, total) = entry is null ? (0, 0) : LibraryActions.Progress(entry);
        progress.Value = total == 0 ? 0 : (double)read / total;
        progressText.Text = total == 0 ? "No chapters" : $"{read} / {total}";
        AutomationProperties.SetName(this, $"{data.Title}, {source.Text}, {(data.Badge > 0 ? $"{data.Badge} unread" : "no unread")}, read {read} of {total}");
        AutomationProperties.SetName(progress, $"Read {read} of {total}");
        LoadNext(data.MangaId, entry);
    }

    private void LoadNext(long mangaId, LibraryEntry? entry)
    {
        var key = $"{mangaId}|{entry?.UnreadCount}|{entry?.ChapterCount}|{entry?.LastReadAt}";
        if (key == nextKey)
        {
            return;
        }
        nextKey = key;
        var my = ++version;
        next.Text = string.Empty;
        if (Services.Engine is not { } engine)
        {
            return;
        }
        _ = Log.Guard("next-library-row", async () =>
        {
            var groups = ChapterGrouping.Group(await engine.Library.GetChaptersAsync(mangaId), mangaId);
            if (my != version)
            {
                return;
            }
            next.Text = ChapterGrouping.NextToRead(groups) switch
            {
                null => "No chapters",
                { Read: true } => "Caught up",
                { Number: >= 0 } group => "Ch. " + Format.ChapterNumber(group.Number),
                var group => group.Primary.Name,
            };
        });
    }

    private static TextBlock Text(string style, bool primary) => new()
    {
        Style = LibraryActions.Style(style),
        Foreground = LibraryActions.Brush(primary ? "TextFillColorPrimaryBrush" : "TextFillColorSecondaryBrush"),
        TextTrimming = TextTrimming.CharacterEllipsis,
        TextWrapping = TextWrapping.NoWrap,
        VerticalAlignment = VerticalAlignment.Center,
    };

    private static void Place(Grid grid, FrameworkElement element, int column)
    {
        Grid.SetColumn(element, column);
        grid.Children.Add(element);
    }
}
