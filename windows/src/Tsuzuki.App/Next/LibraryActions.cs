using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

internal static class LibraryActions
{
    public static FontFamily Symbols => (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];

    public static Brush Brush(string key) => (Brush)Application.Current.Resources[key];

    public static Style Style(string key) => (Style)Application.Current.Resources[key];

    public static FontIcon Glyph(string glyph, double size = 16) => new() { Glyph = glyph, FontFamily = Symbols, FontSize = size };

    public static (int Read, int Total) Progress(LibraryEntry entry) =>
        (Math.Max(entry.ChapterCount - entry.UnreadCount, 0), entry.ChapterCount);

    public static void ShowFlyout(FrameworkElement target, ContextRequestedEventArgs? e, long mangaId, string title, LibraryEntry? entry, FrameworkElement cover)
    {
        var flyout = new CommandBarFlyout();
        AppBarButton Command(string glyph, string label, Func<Task> action, bool primary)
        {
            var button = new AppBarButton { Label = label, Icon = Glyph(glyph) };
            AutomationProperties.SetName(button, label);
            if (primary)
            {
                ToolTipService.SetToolTip(button, label);
            }
            button.Click += (_, _) =>
            {
                flyout.Hide();
                Ui.Enqueue(target.DispatcherQueue, action, "next-library");
            };
            return button;
        }
        var hasUnread = entry is null || entry.UnreadCount > 0;
        flyout.PrimaryCommands.Add(Command("\uE768", entry?.LastReadAt > 0 ? "Continue" : "Read", () => MangaActions.ContinueAsync(mangaId, cover), true));
        flyout.PrimaryCommands.Add(Command("\uE946", "Open details", () => AppNav.OpenDetailsAsync(mangaId, cover), true));
        var markNext = Command("\uE73E", "Mark one ahead read", () => MangaActions.MarkNextReadAsync(mangaId), true);
        markNext.IsEnabled = hasUnread;
        flyout.PrimaryCommands.Add(markNext);
        if (entry is not null)
        {
            flyout.SecondaryCommands.Add(Command("\uE8EC", "Set categories…", () => MangaActions.ShowCategoriesAsync(mangaId, target), false));
            if (MangaActions.MangaDexUrl(entry.Source, entry.Url) is { } url)
            {
                flyout.SecondaryCommands.Add(Command("\uE71B", "Copy MangaDex link", () =>
                {
                    MangaActions.CopyLink(url);
                    return Task.CompletedTask;
                }, false));
            }
        }
        var markAll = Command("\uE8FB", "Mark all read", () => MangaActions.MarkAllReadAsync(mangaId), false);
        markAll.IsEnabled = hasUnread;
        flyout.SecondaryCommands.Add(markAll);
        if (entry is not null)
        {
            flyout.SecondaryCommands.Add(new AppBarSeparator());
            flyout.SecondaryCommands.Add(Command("\uE74D", "Remove from library", () => MangaActions.RemoveFromLibraryAsync(mangaId, title), false));
        }
        var options = new FlyoutShowOptions { ShowMode = FlyoutShowMode.Standard };
        if (e is not null && e.TryGetPosition(target, out var point))
        {
            options.Position = point;
        }
        flyout.ShowAt(target, options);
    }
}
