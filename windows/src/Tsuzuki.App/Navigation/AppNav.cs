using Microsoft.UI.Xaml;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Pages;
using Tsuzuki.App.Reader;

namespace Tsuzuki.App.Navigation;

public static class AppNav
{
    public static Task OpenDetailsAsync(long mangaId, MangaTile? from) =>
        App.MainWindow.Nav.PushAsync(new DetailsPage(mangaId, from));

    public static Task OpenReaderAsync(long mangaId, long chapterId, int page, FrameworkElement? from) =>
        ReaderPage.OpenAsync(mangaId, chapterId, page, from);
}
