using Microsoft.UI.Xaml.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Downloads;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App;

public static class DownloadActions
{
    public static ChapterDownloadStatus StatusOf(ChapterRow chapter) =>
        Services.Engine?.Downloads.StatusOf(chapter.OwnerMangaId, chapter.Url) ?? ChapterDownloadStatus.None;

    public static async Task DownloadAsync(IReadOnlyList<ChapterRow> chapters)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var wanted = chapters.Where(c => c.ExternalUrl is null && StatusOf(c).State is ChapterDownloadState.None or ChapterDownloadState.Failed).ToList();
        if (wanted.Count == 0)
        {
            Toasts.Show("Already downloaded", "Those chapters are downloaded or queued.", Icons.Download, seconds: 3, key: "downloads");
            return;
        }
        var added = await engine.Downloads.EnqueueAsync([.. wanted.Select(c => c.ChapterId)]);
        var note = engine.Downloads.Block switch
        {
            DownloadBlock.Paused => "Downloads are paused. Resume them in Downloads.",
            DownloadBlock.Metered => "Waiting for an unmetered connection.",
            DownloadBlock.LowSpace => "Waiting for free disk space.",
            _ => null,
        };
        Toasts.Show(added == 1 ? "Downloading 1 chapter" : $"Downloading {added} chapters", note, Icons.Download, "View", lead => _ = App.MainWindow.NavigateRootAsync("downloads"), key: "downloads");
    }

    public static async Task DeleteAsync(IReadOnlyList<ChapterRow> chapters)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var downloaded = chapters.Where(c => StatusOf(c).State == ChapterDownloadState.Downloaded).ToList();
        var queued = chapters
            .Select(c => engine.Downloads.ItemOf(c.OwnerMangaId, c.Url))
            .OfType<DownloadItem>()
            .Select(i => i.Id)
            .ToList();
        if (queued.Count > 0)
        {
            await engine.Downloads.CancelAsync(queued);
        }
        if (downloaded.Count > 0)
        {
            var removed = await engine.Downloads.DeleteAsync([.. downloaded.Select(c => (c.OwnerMangaId, c.Url))]);
            Toasts.Show(removed == 1 ? "Deleted 1 download" : $"Deleted {removed} downloads", null, Icons.Check, seconds: 3, key: "downloads");
        }
    }

    public static MenuFlyoutSubItem SeriesMenu(IReadOnlyList<ChapterGroup> groups)
    {
        var menu = new MenuFlyoutSubItem { Text = "Download", Icon = new FontIcon { Glyph = "\uE896" } };
        var unread = groups.Where(g => !g.Read).OrderBy(g => g.Number < 0 ? double.MaxValue : g.Number).Select(g => g.Primary).Where(c => c.Readable).ToList();
        foreach (var count in new[] { 5, 10, 25 })
        {
            var item = new MenuFlyoutItem { Text = $"Next {count} unread", IsEnabled = unread.Count > 0 };
            item.Click += (_, _) => _ = Log.Guard("download-next", () => DownloadAsync(unread.Take(count).ToList()));
            menu.Items.Add(item);
        }
        var allUnread = new MenuFlyoutItem { Text = $"All unread ({unread.Count})", IsEnabled = unread.Count > 0 };
        allUnread.Click += (_, _) => _ = Log.Guard("download-unread", () => DownloadAsync(unread));
        menu.Items.Add(allUnread);
        var everything = groups.Select(g => g.Primary).Where(c => c.Readable).ToList();
        var all = new MenuFlyoutItem { Text = $"All chapters ({everything.Count})", IsEnabled = everything.Count > 0 };
        all.Click += (_, _) => _ = Log.Guard("download-all", () => DownloadAsync(everything));
        menu.Items.Add(all);
        var downloaded = groups.SelectMany(g => g.Alternates.Prepend(g.Primary)).Where(c => StatusOf(c).State == ChapterDownloadState.Downloaded).ToList();
        if (downloaded.Count > 0)
        {
            menu.Items.Add(new MenuFlyoutSeparator());
            var delete = new MenuFlyoutItem { Text = $"Delete {downloaded.Count} downloaded" };
            delete.Click += (_, _) => _ = Log.Guard("download-delete-series", async () =>
            {
                if (await Dialogs.ConfirmAsync($"Delete {downloaded.Count} downloaded chapters?", "The files are removed from this PC. Reading progress is kept.", "Delete"))
                {
                    await DeleteAsync(downloaded);
                }
            });
            menu.Items.Add(delete);
        }
        return menu;
    }

    public static MenuFlyoutItem ChapterItem(ChapterGroup group)
    {
        var chapter = group.Primary;
        var status = StatusOf(chapter);
        var item = status.State switch
        {
            ChapterDownloadState.Downloaded => new MenuFlyoutItem { Text = "Delete download", Icon = new FontIcon { Glyph = "\uE74D" } },
            ChapterDownloadState.Queued or ChapterDownloadState.Downloading => new MenuFlyoutItem { Text = "Cancel download", Icon = new FontIcon { Glyph = "\uE711" } },
            ChapterDownloadState.Failed => new MenuFlyoutItem { Text = "Retry download", Icon = new FontIcon { Glyph = "\uE72C" } },
            _ => new MenuFlyoutItem { Text = "Download", Icon = new FontIcon { Glyph = "\uE896" }, IsEnabled = chapter.Readable && chapter.ExternalUrl is null },
        };
        item.Click += (_, _) => _ = Log.Guard("download-chapter", () => status.State is ChapterDownloadState.None or ChapterDownloadState.Failed
            ? DownloadAsync([chapter])
            : DeleteAsync([chapter]));
        return item;
    }
}
