using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Navigation;

namespace Tsuzuki.App.Next;

public abstract partial class ListRowPage : TzPage, ITzPage, ICoverHost
{
    private readonly TextBlock summary = new() { Style = Resource<Style>("CaptionTextBlockStyle") };
    private readonly StackPanel empty = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Spacing = 8, MaxWidth = 420, Visibility = Visibility.Collapsed };
    private readonly FontIcon emptyIcon = new() { FontSize = 48, Margin = new Thickness(0, 0, 0, 8) };
    private readonly TextBlock emptyTitle = new() { Style = Resource<Style>("SubtitleTextBlockStyle"), HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center };
    private readonly TextBlock emptyBody = new() { Style = Resource<Style>("BodyTextBlockStyle"), HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap };
    private Tsuzuki.Presentation.IFeed? feed;
    private bool shown;
    private bool firstLoad = true;

    protected ListRowPage(string heading, string? secondaryLabel, FrameworkElement? headerTrailing = null)
    {
        List = new ListRowView(heading, secondaryLabel);
        List.Invoked += (_, e) => _ = Log.Guard("next-list-open", () => AppNav.OpenReaderAsync(e.Item.MangaId, e.Item.ChapterId, e.Item.Page, e.Cover));
        summary.Foreground = Resource<Brush>("TextFillColorSecondaryBrush");
        emptyIcon.FontFamily = Resource<FontFamily>("SymbolThemeFontFamily");
        emptyIcon.Foreground = Resource<Brush>("TextFillColorTertiaryBrush");
        emptyBody.Foreground = Resource<Brush>("TextFillColorSecondaryBrush");
        empty.Children.Add(emptyIcon);
        empty.Children.Add(emptyTitle);
        empty.Children.Add(emptyBody);

        var title = new TextBlock { Text = heading, Style = Resource<Style>("TitleTextBlockStyle") };
        AutomationProperties.SetHeadingLevel(title, Microsoft.UI.Xaml.Automation.Peers.AutomationHeadingLevel.Level1);
        var titles = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Bottom };
        titles.Children.Add(title);
        titles.Children.Add(summary);
        var header = new Grid { Padding = new Thickness(36, 28, 36, 4) };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        header.Children.Add(titles);
        if (headerTrailing is not null)
        {
            headerTrailing.VerticalAlignment = VerticalAlignment.Bottom;
            Grid.SetColumn(headerTrailing, 1);
            header.Children.Add(headerTrailing);
        }

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.Children.Add(header);
        Grid.SetRow(List, 1);
        root.Children.Add(List);
        Grid.SetRow(empty, 1);
        root.Children.Add(empty);
        Content = root;

        if (Services.Engine is { } engine)
        {
            feed = CreateFeed(engine);
        }
        else
        {
            AppHost.EngineReady += (_, _) => Ui.Enqueue(DispatcherQueue, () =>
            {
                if (Services.Engine is { } ready && feed is null)
                {
                    feed = CreateFeed(ready);
                    if (shown)
                    {
                        feed.Activate();
                    }
                }
            }, "next-list-ready");
        }
        Ui.ReaderClosed += (_, _) => feed?.ReaderClosed();
    }

    protected ListRowView List { get; }

    public IEnumerable<FrameworkElement> CoversOf(long mangaId) => List.CoversOf(mangaId);

    protected abstract Tsuzuki.Presentation.IFeed CreateFeed(Tsuzuki.Core.Engine engine);

    protected Tsuzuki.Presentation.FeedViewModel<T> Feed<T>(Tsuzuki.Core.Engine engine, Func<Tsuzuki.Core.Library.ILibraryStore, Task<IReadOnlyList<T>>> fetch, Action<IReadOnlyList<T>> show)
    {
        var model = new Tsuzuki.Presentation.FeedViewModel<T>(engine.Library, fetch, new DispatcherUi(DispatcherQueue), () => Ui.ReaderOpen);
        model.Changed += (_, _) => show(model.Items);
        return model;
    }

    public virtual void OnShown()
    {
        shown = true;
        feed?.Activate();
    }

    public void OnHidden()
    {
        shown = false;
        feed?.Deactivate();
    }

    protected void Show(IReadOnlyList<ListRowGroup> groups, string caption, string emptyGlyph, string emptyHeading, string emptyText)
    {
        var changed = List.Present(groups);
        summary.Text = caption;
        summary.Visibility = caption.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        emptyIcon.Glyph = emptyGlyph;
        emptyTitle.Text = emptyHeading;
        emptyBody.Text = emptyText;
        var none = List.Count == 0;
        empty.Visibility = none ? Visibility.Visible : Visibility.Collapsed;
        List.Visibility = none ? Visibility.Collapsed : Visibility.Visible;
        if (firstLoad && changed && !none && !Design.Motion.Reduced)
        {
            _ = Design.Motion.FadeIn(List, Design.Motion.Medium1, 12);
        }
        if (!none)
        {
            firstLoad = false;
        }
    }

    protected static T Resource<T>(string key) => (T)Application.Current.Resources[key];
}
