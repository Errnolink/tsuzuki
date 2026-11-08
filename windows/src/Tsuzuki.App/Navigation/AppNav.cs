using Microsoft.UI.Xaml;
using Tsuzuki.App.Design;
using Tsuzuki.App.Pages;
using Tsuzuki.App.Reader;

namespace Tsuzuki.App.Navigation;

public static class AppNav
{
    public static Task OpenDetailsAsync(long mangaId, FrameworkElement? origin)
    {
        DevHarness.Mark("open details input");
        var from = TransitionOrigin.From(origin);
        var snapshot = Next.DetailsPage.Fetch(mangaId);
        return App.MainWindow.Nav.PushAsync(new Next.DetailsPage(mangaId, from, snapshot));
    }

    public static Task OpenReaderAsync(long mangaId, long chapterId, int page, FrameworkElement? origin) =>
        ReaderPage.OpenAsync(mangaId, chapterId, page, TransitionOrigin.From(origin));

    public static async Task OpenBrowseAsync(long sourceId, string? search = null)
    {
        var window = App.MainWindow;
        await window.NavigateRootAsync("browse");
        if (window.Nav.Root("browse") is not BrowsePage browse)
        {
            return;
        }
        if (string.IsNullOrWhiteSpace(search))
        {
            browse.ShowSource(sourceId);
        }
        else
        {
            browse.Search(search.Trim(), sourceId);
        }
        window.ShowBrowseSource(sourceId);
    }
}
