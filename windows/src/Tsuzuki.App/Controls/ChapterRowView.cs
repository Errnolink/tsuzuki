using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Documents;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Controls;

public sealed partial class ChapterRowView : Button
{
    private readonly Grid stateHost = new() { Width = 24, VerticalAlignment = VerticalAlignment.Center };
    private readonly TextBlock line1 = new() { FontSize = 14, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 };
    private readonly TextBlock line2 = new() { FontSize = 12, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1, Margin = new Thickness(0, 3, 0, 0) };
    private readonly Border progress = new() { CornerRadius = new CornerRadius(10), Padding = new Thickness(9, 3, 9, 3), VerticalAlignment = VerticalAlignment.Center };
    private readonly TextBlock progressText = new() { FontSize = 11, FontWeight = FontWeights.SemiBold };
    private readonly FontIcon trailing = new() { FontSize = 14, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(12, 0, 4, 0) };
    private readonly TextBlock date = new() { FontSize = 12, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 0, 0) };

    public ChapterRowView()
    {
        Style = Res<Style>("TzRowButton");
        Height = 60;
        Padding = new Thickness(14, 0, 14, 0);
        ActualThemeChanged += (_, _) => ApplyReadState();
        progress.Child = progressText;
        progress.Background = Design.Theme.Brush("TzAccentSoft");
        progressText.Foreground = Design.Theme.Brush("TzAccent");
        trailing.FontFamily = Res<FontFamily>("TzIconFont");
        trailing.Foreground = Design.Theme.Brush("TzTextTertiary");
        date.Foreground = Design.Theme.Brush("TzTextTertiary");
        line2.Foreground = Design.Theme.Brush("TzTextSecondary");

        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(6, 0, 12, 0) };
        text.Children.Add(line1);
        text.Children.Add(line2);

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(stateHost);
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);
        Grid.SetColumn(progress, 2);
        grid.Children.Add(progress);
        Grid.SetColumn(date, 3);
        grid.Children.Add(date);
        Grid.SetColumn(trailing, 4);
        grid.Children.Add(trailing);
        Content = grid;
    }

    public ChapterGroup? Group { get; private set; }

    private void ApplyReadState()
    {
        if (Group is { } group)
        {
            Bind(group);
        }
    }

    private string ReadTitleKey() => ActualTheme == ElementTheme.Light ? "TzTextSecondary" : "TzTextTertiary";

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public void Bind(ChapterGroup group)
    {
        Group = group;
        var c = group.Primary;
        var read = group.Read;
        stateHost.Children.Clear();
        if (read)
        {
            stateHost.Children.Add(new FontIcon { Glyph = Icons.Check, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 14, Foreground = Design.Theme.Brush("TzTextTertiary") });
        }
        else if (group.InProgress)
        {
            stateHost.Children.Add(new Ellipse { Width = 10, Height = 10, Stroke = Design.Theme.Brush("TzAccent"), StrokeThickness = 2 });
        }
        else
        {
            stateHost.Children.Add(new Ellipse { Width = 8, Height = 8, Fill = Design.Theme.Brush("TzAccent") });
        }

        line1.Inlines.Clear();
        var number = c.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(c.ChapterNumber) : c.Name;
        if (string.IsNullOrWhiteSpace(number))
        {
            number = c.Url.StartsWith("/chapter/", StringComparison.Ordinal) ? "Chapter" : c.Name;
        }
        if (string.IsNullOrWhiteSpace(number))
        {
            number = "Chapter";
        }
        line1.Inlines.Add(new Run { Text = number, FontWeight = FontWeights.SemiBold });
        var title = ChapterTitle(c);
        if (title is not null)
        {
            line1.Inlines.Add(new Run { Text = "  " + title, Foreground = Design.Theme.Brush("TzTextSecondary") });
        }
        line1.Foreground = Design.Theme.Brush(read ? ReadTitleKey() : "TzText");

        var scan = string.IsNullOrWhiteSpace(c.Scanlator) ? null : c.Scanlator!.Trim();
        line2.Text = group.Alternates.Count > 0
            ? scan is null ? $"{group.Alternates.Count + 1} sources" : $"{scan} + {group.Alternates.Count}"
            : scan ?? string.Empty;
        line2.Visibility = line2.Text.Length == 0 ? Visibility.Collapsed : Visibility.Visible;
        line2.Opacity = read ? (ActualTheme == ElementTheme.Light ? 0.95 : 0.88) : 1;

        var page = group.InProgress ? Math.Max(c.LastPageRead, group.Alternates.Select(a => a.LastPageRead).DefaultIfEmpty(0).Max()) : 0;
        progress.Visibility = group.InProgress ? Visibility.Visible : Visibility.Collapsed;
        progressText.Text = c.PageCount is > 0 ? $"Page {page + 1} of {c.PageCount}" : $"Page {page + 1}";

        date.Text = Format.Relative(c.DateUpload);
        if (!c.Readable)
        {
            trailing.Glyph = Icons.Smartphone;
            trailing.Visibility = Visibility.Visible;
            ToolTipService.SetToolTip(this, "This source reads on your phone");
        }
        else if (c.ExternalUrl is not null)
        {
            trailing.Glyph = Icons.ExternalLink;
            trailing.Visibility = Visibility.Visible;
            ToolTipService.SetToolTip(this, "Opens on the publisher's site");
        }
        else
        {
            trailing.Visibility = Visibility.Collapsed;
            ToolTipService.SetToolTip(this, null);
        }
        AutomationProperties.SetName(this, $"{number} {title} {scan} {(read ? "read" : group.InProgress ? "in progress" : "unread")}".TrimEnd());
    }

    private static string? ChapterTitle(ChapterRow c)
    {
        var name = c.Name;
        var dash = name.IndexOf(" - ", StringComparison.Ordinal);
        if (dash >= 0 && dash + 3 < name.Length)
        {
            return name[(dash + 3)..];
        }
        if (c.ChapterNumber < 0)
        {
            return null;
        }
        var label = "Chapter " + Format.ChapterNumber(c.ChapterNumber);
        if (name.Trim().Equals(label, StringComparison.OrdinalIgnoreCase))
        {
            return null;
        }
        return name.StartsWith("Ch.", StringComparison.OrdinalIgnoreCase) || name.StartsWith("Vol.", StringComparison.OrdinalIgnoreCase) ? null : name;
    }
}

public sealed partial class ChapterRowFactory(Action<ChapterRowView> onClick, Action<ChapterRowView> onContext) : IElementFactory
{
    private readonly Stack<ChapterRowView> pool = new();

    public UIElement GetElement(ElementFactoryGetArgs args)
    {
        var row = pool.Count > 0 ? pool.Pop() : Create();
        row.Bind((ChapterGroup)args.Data);
        return row;
    }

    public void RecycleElement(ElementFactoryRecycleArgs args)
    {
        if (args.Element is ChapterRowView row)
        {
            pool.Push(row);
        }
    }

    private ChapterRowView Create()
    {
        var row = new ChapterRowView();
        row.Click += (s, _) => onClick((ChapterRowView)s);
        row.ContextRequested += (s, e) =>
        {
            e.Handled = true;
            onContext((ChapterRowView)s);
        };
        return row;
    }
}
