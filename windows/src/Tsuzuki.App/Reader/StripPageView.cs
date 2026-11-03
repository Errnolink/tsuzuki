using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Reader;

public sealed partial class StripPageView : Grid
{
    private readonly StackPanel slices = new();
    private readonly Border placeholder = new() { Background = new SolidColorBrush(Windows.UI.Color.FromArgb(18, 255, 255, 255)) };
    private readonly TextBlock placeholderText = new() { Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(120, 255, 255, 255)), FontSize = 13, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };

    public StripPageView()
    {
        placeholder.Child = placeholderText;
        Children.Add(placeholder);
        Children.Add(slices);
    }

    public int Page { get; private set; } = -1;
    public double Aspect { get; private set; } = 1.45;

    public void Prepare(int page, double width, double aspect)
    {
        Page = page;
        Aspect = aspect;
        Width = width;
        Height = width * aspect;
        slices.Children.Clear();
        placeholder.Visibility = Visibility.Visible;
        placeholderText.Text = (page + 1).ToString();
    }

    public void SetPage(DecodedPage page, IReadOnlyList<SoftwareBitmapSource> sources)
    {
        Aspect = page.Aspect;
        Height = Width * page.Aspect;
        slices.Children.Clear();
        var total = page.Slices.Sum(s => s.Height);
        for (var i = 0; i < sources.Count; i++)
        {
            slices.Children.Add(new Image
            {
                Source = sources[i],
                Stretch = Stretch.Fill,
                Width = Width,
                Height = Height * page.Slices[i].Height / Math.Max(1, total),
            });
        }
        placeholder.Visibility = Visibility.Collapsed;
    }

    public void ShowError(Action retry)
    {
        placeholderText.Text = "Couldn't load page " + (Page + 1) + ". Click to retry.";
        placeholder.Visibility = Visibility.Visible;
        void Handler(object s, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
        {
            placeholder.Tapped -= Handler;
            e.Handled = true;
            placeholderText.Text = (Page + 1).ToString();
            retry();
        }
        placeholder.Tapped += Handler;
    }

    public void ShowFooter(ChapterRow? next, Action goNext)
    {
        slices.Children.Clear();
        placeholder.Visibility = Visibility.Collapsed;
        Height = 260;
        var res = Application.Current.Resources;
        var stack = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Spacing = 10 };
        stack.Children.Add(new TextBlock { Text = "END OF CHAPTER", Style = (Style)res["TzKicker"], Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(150, 255, 255, 255)), HorizontalAlignment = HorizontalAlignment.Center });
        stack.Children.Add(new TextBlock
        {
            Text = next is null ? "You're all caught up" : "Up next: " + (next.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(next.ChapterNumber) : next.Name),
            Style = (Style)res["TzTitle"],
            Foreground = new SolidColorBrush(Microsoft.UI.Colors.White),
            HorizontalAlignment = HorizontalAlignment.Center,
        });
        if (next is not null)
        {
            var button = new Button { Style = (Style)res["TzAccentButton"], Content = "Next chapter", HorizontalAlignment = HorizontalAlignment.Center };
            button.Click += (_, _) => goNext();
            stack.Children.Add(button);
        }
        slices.Children.Add(new Grid { Height = 260, Width = Width, Children = { stack } });
    }
}

public sealed partial class StripFactory(ReaderPage reader) : IElementFactory
{
    private readonly Stack<StripPageView> pool = new();
    private readonly Dictionary<int, StripPageView> realized = [];
    private readonly Dictionary<int, double> aspects = [];

    public double Width { get; set; } = 800;

    public UIElement GetElement(ElementFactoryGetArgs args)
    {
        var page = (int)args.Data;
        var view = pool.Count > 0 ? pool.Pop() : new StripPageView();
        view.Prepare(page, Width, aspects.GetValueOrDefault(page, 1.45));
        realized[page] = view;
        _ = BindAsync(view, page);
        return view;
    }

    private async Task BindAsync(StripPageView view, int page)
    {
        await reader.BindStripPageAsync(view, page);
        if (view.Page == page)
        {
            aspects[page] = view.Aspect;
        }
    }

    public void RecycleElement(ElementFactoryRecycleArgs args)
    {
        if (args.Element is StripPageView view)
        {
            realized.Remove(view.Page);
            pool.Push(view);
        }
    }

    public bool IsRealized(int page) => realized.ContainsKey(page);

    public int? PageAt(double y)
    {
        foreach (var (page, view) in realized)
        {
            var top = view.ActualOffset.Y;
            if (y >= top && y < top + view.ActualHeight)
            {
                return page;
            }
        }
        return null;
    }

    public void Reset()
    {
        realized.Clear();
        aspects.Clear();
    }
}
