using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;
using Windows.ApplicationModel.DataTransfer;

namespace Tsuzuki.App;

public static class MangaActions
{
    private const long MangaDexSource = 2499283573021220255;

    public static Uri? MangaDexUrl(long source, string url) =>
        source == MangaDexSource && url.StartsWith("/manga/", StringComparison.Ordinal)
            ? new Uri("https://mangadex.org/title/" + url["/manga/".Length..])
            : null;

    public static async Task ContinueAsync(long mangaId, FrameworkElement? from)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var groups = ChapterGrouping.Group(await engine.Library.GetChaptersAsync(mangaId), mangaId);
        if (ChapterGrouping.NextToRead(groups) is { } next)
        {
            await OpenGroupAsync(mangaId, next, from);
        }
        else
        {
            Toasts.Show("No chapters yet", "Open the details to check the source for chapters.", Icons.Info);
        }
    }

    public static async Task OpenGroupAsync(long mangaId, ChapterGroup group, FrameworkElement? from)
    {
        var copy = group.Primary.Readable && group.Primary.ExternalUrl is null
            ? group.Primary
            : group.Alternates.FirstOrDefault(a => a.Readable && a.ExternalUrl is null);
        if (copy is null)
        {
            if (group.Primary.ExternalUrl is { } external && Uri.TryCreate(external, UriKind.Absolute, out var uri))
            {
                await Windows.System.Launcher.LaunchUriAsync(uri);
                return;
            }
            Toasts.Show("Read this one on your phone", "Its source isn't available on Windows yet. Your progress still syncs.", Icons.Smartphone);
            return;
        }
        var page = group.Read ? 0 : Math.Max(copy.LastPageRead, 0);
        await AppNav.OpenReaderAsync(mangaId, copy.ChapterId, page, from);
    }

    public static async Task MarkNextReadAsync(long mangaId)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var groups = ChapterGrouping.Group(await engine.Library.GetChaptersAsync(mangaId), mangaId);
        if (ChapterGrouping.NextToRead(groups) is not { Read: false } next)
        {
            Toasts.Show("You're caught up", null, Icons.CircleCheck, seconds: 3);
            return;
        }
        await engine.Library.SetChaptersReadAsync(new[] { next.Primary }.Concat(next.Alternates).Select(c => c.ChapterId).ToList(), true);
        var number = next.Number >= 0 ? "Ch. " + Format.ChapterNumber(next.Number) : next.Primary.Name;
        Toasts.Show($"Marked {number} as read", null, Icons.Check, seconds: 3);
    }

    public static async Task MarkAllReadAsync(long mangaId)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var chapters = await engine.Library.GetChaptersAsync(mangaId);
        var unread = chapters.Where(c => !c.Read).Select(c => c.ChapterId).ToList();
        if (unread.Count > 0)
        {
            await engine.Library.SetChaptersReadAsync(unread, true);
        }
        Toasts.Show("Marked all as read", null, Icons.CheckCheck, seconds: 3);
    }

    public static async Task RemoveFromLibraryAsync(long mangaId, string title)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        if (!await Dialogs.ConfirmDestructiveAsync($"Remove {title} from library?", "Your reading progress is kept. Your phone drops it from its library at the next sync.", "Remove", "Keep"))
        {
            return;
        }
        await engine.Library.SetFavoriteAsync(mangaId, false);
        Toasts.Show("Removed from your library", null, Icons.Close);
    }

    public static void CopyLink(Uri url)
    {
        var package = new DataPackage();
        package.SetText(url.ToString());
        Clipboard.SetContent(package);
        Toasts.Show("Copied", "MangaDex link", Icons.Copy, seconds: 2);
    }

    public static async Task ShowCategoriesAsync(long mangaId, FrameworkElement anchor)
    {
        if (Services.Engine is not { } engine || await engine.Library.GetMangaAsync(mangaId) is not { } manga)
        {
            return;
        }
        var categories = await engine.Library.GetCategoriesAsync();
        var menu = new MenuFlyout { Placement = FlyoutPlacementMode.Bottom };
        var selected = manga.CategoryUids.ToHashSet();
        foreach (var category in categories.OrderBy(c => c.Order))
        {
            var item = new ToggleMenuFlyoutItem { Text = category.Name, IsChecked = selected.Contains(category.Uid) };
            item.Click += (_, _) =>
            {
                if (item.IsChecked)
                {
                    selected.Add(category.Uid);
                }
                else
                {
                    selected.Remove(category.Uid);
                }
                _ = Log.Guard("category-set", () => engine.Library.SetMangaCategoriesAsync(mangaId, selected.ToList()));
            };
            menu.Items.Add(item);
        }
        if (categories.Count > 0)
        {
            menu.Items.Add(new MenuFlyoutSeparator());
        }
        var create = new MenuFlyoutItem { Text = "New category…", Icon = new FontIcon { Glyph = Icons.Plus, FontFamily = (FontFamily)Application.Current.Resources["TzIconFont"] } };
        create.Click += (_, _) => _ = Log.Guard("category-create", async () =>
        {
            var name = await Dialogs.PromptAsync("New category", "Name", "Create");
            if (!string.IsNullOrWhiteSpace(name))
            {
                var category = await engine.Library.CreateCategoryAsync(name.Trim());
                selected.Add(category.Uid);
                await engine.Library.SetMangaCategoriesAsync(mangaId, selected.ToList());
            }
        });
        menu.Items.Add(create);
        menu.ShowAt(anchor);
    }
}
