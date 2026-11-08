using Microsoft.UI.Input;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;
using Windows.System;
using Windows.UI.Core;

namespace Tsuzuki.App.Next;

public sealed record ChapterRowClick(ChapterTableRow Row, bool Toggle, bool Range);

public sealed partial class ChapterTableRow : Button
{
    public const double RowHeight = 44;

    private readonly CheckBox check = new() { MinWidth = 0, Width = 20, Padding = new Thickness(0), VerticalAlignment = VerticalAlignment.Center, IsTabStop = false };
    private readonly TextBlock number = new() { Style = DetailsTheme.Style("BodyTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 };
    private readonly TextBlock title = new() { Style = DetailsTheme.Style("BodyTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 };
    private readonly Grid downloadMark = new() { Width = 16, Height = 16, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(8, 0, 0, 0), Visibility = Visibility.Collapsed };
    private readonly FontIcon trailing = new() { FontFamily = DetailsTheme.Symbols, FontSize = 12, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(8, 0, 0, 0), Foreground = DetailsTheme.Brush("TextFillColorTertiaryBrush") };
    private readonly TextBlock scanlator = new() { Style = DetailsTheme.Style("BodyTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1, Margin = new Thickness(0, 0, 12, 0) };
    private readonly TextBlock date = new() { Style = DetailsTheme.Style("BodyTextBlockStyle"), VerticalAlignment = VerticalAlignment.Center };
    private readonly Grid progressHost = new() { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Stretch, Margin = new Thickness(0, 0, 12, 0) };
    private readonly StackPanel hoverActions = new() { Orientation = Orientation.Horizontal, Spacing = 2, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center, Visibility = Visibility.Collapsed };
    private readonly Border selectionFill = new() { CornerRadius = new CornerRadius(4), IsHitTestVisible = false, Visibility = Visibility.Collapsed };
    private readonly Button markPrevious;
    private readonly Button toggleRead;
    private readonly Button more;
    private bool pointerOver;
    private bool selected;
    private bool selectionMode;

    public ChapterTableRow()
    {
        Height = RowHeight;
        Padding = new Thickness(0);
        HorizontalAlignment = HorizontalAlignment.Stretch;
        HorizontalContentAlignment = HorizontalAlignment.Stretch;
        VerticalContentAlignment = VerticalAlignment.Stretch;
        Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        BorderThickness = new Thickness(0);
        CornerRadius = new CornerRadius(4);
        selectionFill.Background = DetailsTheme.Brush("SubtleFillColorSecondaryBrush");
        selectionFill.BorderBrush = DetailsTheme.Brush("AccentFillColorDefaultBrush");
        selectionFill.BorderThickness = new Thickness(3, 0, 0, 0);

        toggleRead = ActionButton("\uE8FB", "Mark as read");
        markPrevious = ActionButton("\uE762", "Mark previous as read");
        more = ActionButton("\uE712", "More chapter actions");
        hoverActions.Children.Add(toggleRead);
        hoverActions.Children.Add(markPrevious);
        hoverActions.Children.Add(more);
        toggleRead.Click += (_, _) => ToggleReadRequested?.Invoke(this, EventArgs.Empty);
        markPrevious.Click += (_, _) => MarkPreviousRequested?.Invoke(this, EventArgs.Empty);
        more.Click += (_, _) => MenuRequested?.Invoke(this, more);
        check.Click += (_, _) => SelectRequested?.Invoke(this, new ChapterRowClick(this, Toggle: true, Range: false));

        var titleCell = new Grid();
        titleCell.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        titleCell.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        titleCell.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        titleCell.Children.Add(title);
        Grid.SetColumn(trailing, 1);
        titleCell.Children.Add(trailing);
        Grid.SetColumn(downloadMark, 2);
        titleCell.Children.Add(downloadMark);
        titleCell.ColumnDefinitions[0].Width = new GridLength(1, GridUnitType.Star);
        titleCell.Margin = new Thickness(0, 0, 12, 0);

        var grid = ChapterColumns.Create();
        grid.Padding = new Thickness(12, 0, 8, 0);
        grid.Children.Add(selectionFill);
        Grid.SetColumnSpan(selectionFill, ChapterColumns.Count);
        selectionFill.Margin = new Thickness(-12, 2, -8, 2);
        grid.Children.Add(check);
        Place(grid, number, 1);
        Place(grid, titleCell, 2);
        Place(grid, scanlator, 3);
        Place(grid, date, 4);
        Place(grid, progressHost, 5);
        Place(grid, hoverActions, 6);
        Content = grid;

        PointerEntered += (_, _) => SetPointer(true);
        PointerExited += (_, _) => SetPointer(false);
        PointerCanceled += (_, _) => SetPointer(false);
        GotFocus += (_, _) => UpdateChrome();
        LostFocus += (_, _) => UpdateChrome();
        Click += (_, _) =>
        {
            var ctrl = Down(VirtualKey.Control);
            var shift = Down(VirtualKey.Shift);
            if (ctrl || shift || selectionMode)
            {
                SelectRequested?.Invoke(this, new ChapterRowClick(this, Toggle: !shift, Range: shift));
            }
            else
            {
                OpenRequested?.Invoke(this, EventArgs.Empty);
            }
        };
        PreviewKeyDown += OnPreviewKeyDown;
        ContextRequested += (_, e) =>
        {
            e.Handled = true;
            MenuRequested?.Invoke(this, this);
        };
    }

    public ChapterGroup? Group { get; private set; }

    public event EventHandler? OpenRequested;

    public event EventHandler<ChapterRowClick>? SelectRequested;

    public event EventHandler? ToggleReadRequested;

    public event EventHandler? MarkPreviousRequested;

    public event EventHandler<FrameworkElement>? MenuRequested;

    private static bool Down(VirtualKey key) =>
        InputKeyboardSource.GetKeyStateForCurrentThread(key).HasFlag(CoreVirtualKeyStates.Down);

    private static void Place(Grid grid, FrameworkElement element, int column)
    {
        Grid.SetColumn(element, column);
        grid.Children.Add(element);
    }

    private void OnPreviewKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Space && (Down(VirtualKey.Control) || selectionMode))
        {
            e.Handled = true;
            SelectRequested?.Invoke(this, new ChapterRowClick(this, Toggle: !Down(VirtualKey.Shift), Range: Down(VirtualKey.Shift)));
        }
        else if (e.Key is VirtualKey.Up or VirtualKey.Down)
        {
            var target = FocusManager.FindNextElement(e.Key == VirtualKey.Up ? FocusNavigationDirection.Up : FocusNavigationDirection.Down, new FindNextElementOptions { SearchRoot = XamlRoot.Content });
            if (target is ChapterTableRow row)
            {
                e.Handled = true;
                row.Focus(FocusState.Keyboard);
                row.StartBringIntoView();
                if (Down(VirtualKey.Shift))
                {
                    row.SelectRequested?.Invoke(row, new ChapterRowClick(row, Toggle: false, Range: true));
                }
            }
        }
    }

    private Button ActionButton(string glyph, string name)
    {
        var button = new Button
        {
            Width = 32,
            Height = 32,
            MinHeight = 0,
            MinWidth = 0,
            Padding = new Thickness(0),
            CornerRadius = new CornerRadius(4),
            Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
            BorderThickness = new Thickness(0),
            Content = new FontIcon { Glyph = glyph, FontFamily = DetailsTheme.Symbols, FontSize = 14 },
        };
        ToolTipService.SetToolTip(button, name);
        AutomationProperties.SetName(button, name);
        return button;
    }

    private void SetPointer(bool over)
    {
        pointerOver = over;
        UpdateChrome();
    }

    private void UpdateChrome()
    {
        var focused = FocusState != FocusState.Unfocused || hoverActions.Children.OfType<Control>().Any(c => c.FocusState != FocusState.Unfocused);
        hoverActions.Visibility = (pointerOver || focused) && !selectionMode ? Visibility.Visible : Visibility.Collapsed;
        check.Visibility = pointerOver || focused || selectionMode ? Visibility.Visible : Visibility.Collapsed;
        number.Opacity = check.Visibility == Visibility.Visible ? 0 : 1;
        selectionFill.Visibility = selected ? Visibility.Visible : Visibility.Collapsed;
    }

    public void SetSelection(bool isSelected, bool inSelectionMode)
    {
        selected = isSelected;
        selectionMode = inSelectionMode;
        check.IsChecked = isSelected;
        AutomationProperties.SetItemStatus(this, isSelected ? "Selected" : string.Empty);
        UpdateChrome();
    }

    public void Bind(ChapterGroup group, bool isSelected, bool inSelectionMode)
    {
        Group = group;
        pointerOver = false;
        var c = group.Primary;
        var read = group.Read;
        var primaryText = DetailsTheme.Brush(read ? "TextFillColorTertiaryBrush" : "TextFillColorPrimaryBrush");
        var secondaryText = DetailsTheme.Brush(read ? "TextFillColorTertiaryBrush" : "TextFillColorSecondaryBrush");

        number.Text = c.ChapterNumber >= 0 ? Format.ChapterNumber(c.ChapterNumber) : "–";
        number.Foreground = secondaryText;
        var name = ChapterTitle(c);
        var label = c.ChapterNumber >= 0 ? "Chapter " + Format.ChapterNumber(c.ChapterNumber) : string.IsNullOrWhiteSpace(c.Name) ? "Chapter" : c.Name;
        title.Text = name ?? label;
        title.FontWeight = read ? FontWeights.Normal : FontWeights.SemiBold;
        title.Foreground = primaryText;

        var scan = string.IsNullOrWhiteSpace(c.Scanlator) ? null : c.Scanlator!.Trim();
        scanlator.Text = group.Alternates.Count > 0
            ? scan is null ? $"{group.Alternates.Count + 1} copies" : $"{scan} +{group.Alternates.Count}"
            : scan ?? string.Empty;
        scanlator.Foreground = secondaryText;
        date.Text = c.DateUpload > 0 ? Format.Relative(c.DateUpload) : string.Empty;
        date.Foreground = secondaryText;

        progressHost.Children.Clear();
        string state;
        if (read)
        {
            var done = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
            done.Children.Add(new FontIcon { Glyph = "\uE73E", FontFamily = DetailsTheme.Symbols, FontSize = 12, Foreground = DetailsTheme.Brush("SystemFillColorSuccessBrush") });
            done.Children.Add(new TextBlock { Text = "Read", Style = DetailsTheme.Style("CaptionTextBlockStyle"), Foreground = secondaryText, VerticalAlignment = VerticalAlignment.Center });
            progressHost.Children.Add(done);
            state = "read";
        }
        else if (group.InProgress)
        {
            var page = Math.Max(c.LastPageRead, group.Alternates.Select(a => a.LastPageRead).DefaultIfEmpty(0).Max());
            var total = c.PageCount is > 0 ? c.PageCount.Value : 0;
            var stack = new StackPanel { Spacing = 3 };
            stack.Children.Add(new TextBlock { Text = total > 0 ? $"Page {page + 1} of {total}" : $"Page {page + 1}", Style = DetailsTheme.Style("CaptionTextBlockStyle"), Foreground = DetailsTheme.Brush("AccentTextFillColorPrimaryBrush") });
            if (total > 0)
            {
                stack.Children.Add(new ProgressBar { Minimum = 0, Maximum = total, Value = page + 1, MinHeight = 3, IsTabStop = false });
            }
            progressHost.Children.Add(stack);
            state = "in progress, page " + (page + 1);
        }
        else
        {
            progressHost.Children.Add(new Ellipse { Width = 8, Height = 8, Fill = DetailsTheme.Brush("AccentFillColorDefaultBrush"), HorizontalAlignment = HorizontalAlignment.Left });
            state = "unread";
        }

        if (!c.Readable)
        {
            trailing.Glyph = "\uE8EA";
            trailing.Visibility = Visibility.Visible;
            ToolTipService.SetToolTip(this, "This source reads on your phone");
        }
        else if (c.ExternalUrl is not null)
        {
            trailing.Glyph = "\uE8A7";
            trailing.Visibility = Visibility.Visible;
            ToolTipService.SetToolTip(this, "Opens on the publisher's site");
        }
        else
        {
            trailing.Visibility = Visibility.Collapsed;
            ToolTipService.SetToolTip(this, null);
        }

        var downloadState = BindDownload();
        var shortLabel = c.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(c.ChapterNumber) : label;
        AutomationProperties.SetName(this, string.Join(", ", new[] { label, name, scan, date.Text, state, downloadState }.Where(s => !string.IsNullOrWhiteSpace(s))));
        AutomationProperties.SetName(check, "Select " + shortLabel);
        var toggleName = read ? $"Mark {shortLabel} as unread" : $"Mark {shortLabel} as read";
        AutomationProperties.SetName(toggleRead, toggleName);
        ToolTipService.SetToolTip(toggleRead, read ? "Mark as unread" : "Mark as read");
        ((FontIcon)toggleRead.Content).Glyph = read ? "\uE7BA" : "\uE8FB";
        AutomationProperties.SetName(markPrevious, $"Mark chapters before {shortLabel} as read");
        AutomationProperties.SetName(more, $"More actions for {shortLabel}");
        SetSelection(isSelected, inSelectionMode);
    }

    public string? BindDownload()
    {
        downloadMark.Children.Clear();
        if (Group is not { } group)
        {
            downloadMark.Visibility = Visibility.Collapsed;
            return null;
        }
        var status = DownloadActions.StatusOf(group.Primary);
        (UIElement? mark, string? text) = status.State switch
        {
            Tsuzuki.Core.Downloads.ChapterDownloadState.Downloaded => ((UIElement?)new FontIcon { Glyph = "\uE896", FontFamily = DetailsTheme.Symbols, FontSize = 12, Foreground = DetailsTheme.Brush("SystemFillColorSuccessBrush") }, "downloaded"),
            Tsuzuki.Core.Downloads.ChapterDownloadState.Downloading => (new ProgressRing { IsIndeterminate = status.PagesTotal == 0, Minimum = 0, Maximum = 1, Value = status.Progress, Width = 14, Height = 14 }, "downloading"),
            Tsuzuki.Core.Downloads.ChapterDownloadState.Queued => (new FontIcon { Glyph = "\uE896", FontFamily = DetailsTheme.Symbols, FontSize = 12, Foreground = DetailsTheme.Brush("TextFillColorTertiaryBrush") }, "queued for download"),
            Tsuzuki.Core.Downloads.ChapterDownloadState.Failed => (new FontIcon { Glyph = "\uE7BA", FontFamily = DetailsTheme.Symbols, FontSize = 12, Foreground = DetailsTheme.Brush("SystemFillColorCautionBrush") }, "download failed"),
            _ => (null, null),
        };
        if (mark is null)
        {
            downloadMark.Visibility = Visibility.Collapsed;
            return null;
        }
        downloadMark.Children.Add(mark);
        downloadMark.Visibility = Visibility.Visible;
        ToolTipService.SetToolTip(downloadMark, char.ToUpperInvariant(text![0]) + text[1..]);
        return text;
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

public static class ChapterColumns
{
    public const int Count = 7;

    public static Grid Create()
    {
        var grid = new Grid();
        foreach (var width in new[] { new GridLength(28), new GridLength(56), new GridLength(1, GridUnitType.Star), new GridLength(200), new GridLength(120), new GridLength(150), new GridLength(104) })
        {
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = width });
        }
        return grid;
    }

    public static Grid Header()
    {
        var grid = Create();
        grid.Padding = new Thickness(12, 0, 8, 0);
        grid.Height = 36;
        string[] labels = ["", "#", "Title", "Group", "Released", "Progress"];
        for (var i = 1; i < labels.Length; i++)
        {
            var text = new TextBlock { Text = labels[i], Style = DetailsTheme.Style("CaptionTextBlockStyle"), Foreground = DetailsTheme.Brush("TextFillColorSecondaryBrush"), VerticalAlignment = VerticalAlignment.Center };
            Grid.SetColumn(text, i);
            grid.Children.Add(text);
        }
        return grid;
    }
}

public sealed partial class ChapterTableFactory(Func<ChapterGroup, bool> isSelected, Func<bool> selectionMode, Action<ChapterTableRow> wire) : IElementFactory
{
    private readonly Stack<ChapterTableRow> pool = new();

    public UIElement GetElement(ElementFactoryGetArgs args)
    {
        if (!pool.TryPop(out var row))
        {
            row = new ChapterTableRow();
            wire(row);
        }
        var group = (ChapterGroup)args.Data;
        row.Bind(group, isSelected(group), selectionMode());
        return row;
    }

    public void RecycleElement(ElementFactoryRecycleArgs args)
    {
        if (args.Element is ChapterTableRow row)
        {
            pool.Push(row);
        }
    }
}
