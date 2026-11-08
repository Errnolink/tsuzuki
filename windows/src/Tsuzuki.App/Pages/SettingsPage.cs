using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core;
using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Updates;
using Tsuzuki.Presentation;
using Windows.Storage.Pickers;

namespace Tsuzuki.App.Pages;

public sealed partial class SettingsPage : TzPage, ITzPage
{
    private readonly StackPanel body = new() { Padding = new Thickness(36, 28, 36, 56), MaxWidth = 1080, Spacing = 32, HorizontalAlignment = HorizontalAlignment.Stretch };
    private readonly SettingsCard syncCard = new() { Icon = Icons.MonitorSmartphone, Header = "Phone sync" };
    private readonly SettingsCard folderCard = new() { Icon = Icons.HardDrive, Header = "Backup folder", DescriptionSelectable = true };
    private readonly SettingsCard backupNowCard = new() { Icon = Icons.Upload, Header = "Back up now" };
    private readonly SettingsCard driveCard = new() { Icon = Icons.Cloud, Header = "Google Drive", Description = $"Back up into My Drive\\{Tsuzuki.Core.AppIdentity.DriveFolder}\\Backups. Off keeps backups on this PC." };
    private readonly SettingsCard downloadFolderCard = new() { Icon = Icons.Folder, Header = "Download folder", DescriptionSelectable = true };
    private readonly SettingsCard autoDownloadCard = new() { Icon = Icons.Download, Header = "Download new chapters automatically" };
    private readonly SettingsCard dataFolderCard = new() { Header = "Data folder", DescriptionSelectable = true };
    private readonly ComboBox readerMode;
    private readonly ComboBox readerDirection;

    public SettingsPage()
    {
        var scroll = new ScrollView { Content = body, HorizontalScrollMode = ScrollingScrollMode.Disabled, VerticalScrollBarVisibility = ScrollingScrollBarVisibility.Auto };
        Content = scroll;
        var titleBlock = new StackPanel
        {
            Children =
            {
                new TextBlock { Text = "Settings", Style = Res<Style>("TzLargeTitle"), Margin = new Thickness(0, 2, 0, 0) },
            },
        };
        body.Children.Add(titleBlock);

        var theme = new RadioButtons { MaxColumns = 3, ItemsSource = new[] { "System", "Light", "Dark" }, SelectedIndex = (int)Services.Settings.Theme };
        theme.SelectionChanged += (_, _) =>
        {
            if (theme.SelectedIndex < 0 || theme.SelectedIndex == (int)Services.Settings.Theme)
            {
                return;
            }
            Services.Settings.Theme = (ThemeChoice)theme.SelectedIndex;
            Services.Settings.Save();
            _ = AppHost.SwitchThemeAsync();
        };
        var header = Choice(["Centred cover", "Classic"], Services.Settings.DetailsHeader == DetailsHeaderStyle.Centred ? 0 : 1, i =>
        {
            Services.Settings.DetailsHeader = i == 0 ? DetailsHeaderStyle.Centred : DetailsHeaderStyle.Classic;
            Services.Settings.Save();
        });
        AddGroup("Appearance",
            new SettingsCard { Icon = Icons.Sun, Header = "Theme", Description = "Follows Windows when set to System. Tsuzuki restarts to switch.", Content = theme },
            new SettingsCard { Icon = Icons.BookOpen, Header = "Details header", Description = "Centred cover puts the cover in the middle with the title and actions under it. Classic puts the cover on the left. Applies to the next title you open.", Content = header },
            new SettingsCard { Icon = Icons.Sun, Header = "Cover colour behind the header", Description = "Fills the Details header with a darkened colour taken from the cover, like the Apple Books store. Applies to the next title you open.", Content = Toggle(Services.Settings.DetailsCoverColour, on =>
            {
                Services.Settings.DetailsCoverColour = on;
                Services.Settings.Save();
            }) });

        AddGroup("Library",
            new SettingsCard { Icon = Icons.Grid, Header = "Cover size", Description = "Size of covers in the library and browse grids.", Content = CoverSlider() });

        AddGroup("Sources",
            new SettingsCard { Icon = Icons.Layers, Header = "Extensions", Description = "Add Mihon extension stores and install extensions to read more sources.", Content = Action("Manage", false, () => _ = App.MainWindow.NavigateRootAsync("extensions")) });

        downloadFolderCard.Content = Action("Change", false, () => _ = Log.Guard("settings-download-folder", ChangeDownloadFolderAsync));
        autoDownloadCard.Content = Action("Choose", false, () => _ = Log.Guard("settings-auto-download", ChooseAutoDownloadAsync));
        var deleteAfter = Choice(["Never", "Right after reading", "Keep the last 1 read", "Keep the last 3 read"],
            Services.Settings.DownloadsDeleteAfterRead switch { null => 0, 0 => 1, 1 => 2, _ => 3 }, i =>
            {
                Services.Settings.DownloadsDeleteAfterRead = i switch { 0 => null, 1 => 0, 2 => 1, _ => 3 };
                Services.Settings.Save();
                AppHost.ApplySourceOptions();
            });
        var trackingCards = new List<UIElement>();
        foreach (var tracker in Services.Engine?.Tracking.Trackers ?? [])
        {
            var card = new SettingsCard { Icon = Icons.Check, Header = tracker.Name };
            void Bind()
            {
                card.Description = tracker.SignedIn ? "Signed in. Reading on this PC updates your list." : "Not signed in.";
                card.Content = Action(tracker.SignedIn ? "Sign out" : "Sign in", !tracker.SignedIn, () => _ = Log.Guard("settings-tracker", async () =>
                {
                    if (tracker.SignedIn)
                    {
                        tracker.Logout();
                    }
                    else
                    {
                        await TrackingDialog.SignInAsync(XamlRoot, tracker);
                    }
                    Bind();
                }));
            }
            Bind();
            trackingCards.Add(card);
        }
        trackingCards.Add(new SettingsCard
        {
            Icon = Icons.Info,
            Header = "AniList and MyAnimeList",
            Description = "Titles tracked on your phone keep their progress current from the PC through sync. Signing in on the PC arrives once Tsuzuki's desktop app registration with AniList and MyAnimeList is done.",
        });
        trackingCards.Add(new SettingsCard
        {
            Icon = Icons.BookOpen,
            Header = "Update progress after reading",
            Description = "When you finish a chapter, tracked lists move forward. They never move back.",
            Content = Toggle(Services.Settings.TrackingUpdateAfterReading, on =>
            {
                Services.Settings.TrackingUpdateAfterReading = on;
                Services.Settings.Save();
                if (Services.Engine is { } engine)
                {
                    engine.Tracking.UpdateAfterReading = on;
                }
            }),
        });
        AddGroup("Tracking", [.. trackingCards]);

        AddGroup("Downloads",
            downloadFolderCard,
            new SettingsCard { Icon = Icons.Cloud, Header = "Only on unmetered connections", Description = "Pause downloads on metered Wi-Fi or mobile data.", Content = Toggle(Services.Settings.DownloadsUnmeteredOnly, on => { Services.Settings.DownloadsUnmeteredOnly = on; Services.Settings.Save(); AppHost.ApplySourceOptions(); }) },
            new SettingsCard { Icon = Icons.Check, Header = "Delete after reading", Description = "Also applies when you finish a chapter on your phone and it syncs.", Content = deleteAfter },
            autoDownloadCard);

        readerMode = Choice(["Single page", "Two pages", "Long strip"], (int)Services.Settings.ReaderMode, i =>
        {
            if ((int)Services.Settings.ReaderMode == i)
            {
                return;
            }
            Services.Settings.ReaderMode = (ReaderMode)i;
            Services.Settings.Save();
        });
        readerDirection = Choice(["Right to left", "Left to right"], Services.Settings.RightToLeft ? 0 : 1, i =>
        {
            if (Services.Settings.RightToLeft == (i == 0))
            {
                return;
            }
            Services.Settings.RightToLeft = i == 0;
            Services.Settings.Save();
        });
        AddGroup("Reader defaults",
            new SettingsCard { Icon = Icons.Columns, Header = "Default layout", Description = "Long Strip titles open as a strip automatically.", Content = readerMode },
            new SettingsCard { Icon = Icons.ArrowLeft, Header = "Reading direction", Description = "Arrow keys and page clicks follow this.", Content = readerDirection },
            new SettingsCard { Icon = Icons.BookOpen, Header = "Cover page alone", Description = "In two-page view, show the first page by itself so spreads line up.", Content = Toggle(Services.Settings.FirstPageAlone, on => { Services.Settings.FirstPageAlone = on; Services.Settings.Save(); }) },
            new SettingsCard { Icon = Icons.Image, Header = "Data saver", Description = "Load smaller MangaDex images. Faster on slow connections.", Content = Toggle(Services.Settings.DataSaver, on => { Services.Settings.DataSaver = on; Services.Settings.Save(); AppHost.ApplySourceOptions(); }) },
            new SettingsCard { Icon = Icons.Info, Header = "Shortcuts", Description = "← → or click the edges to turn pages · Space next · 1 2 3 layouts · D two pages · O offset · F fit · R direction · G go to page · Home End first/last · [ ] chapters · Ctrl+wheel zoom · F11 full screen · ? all shortcuts · Esc close. Layout, direction, fit and offset are remembered per series." });

        syncCard.Content = Action("Pair a phone", primary: true, () => DevicesPanel.Show());
        AddGroup("Sync",
            syncCard,
            new SettingsCard { Icon = Icons.Refresh, Header = "Keep syncing when closed", Description = "Closing the window leaves Tsuzuki in the tray so your phone can still sync.", Content = Toggle(Services.Settings.KeepSyncing, on => { Services.Settings.KeepSyncing = on; Services.Settings.Save(); }) },
            new SettingsCard { Icon = Icons.Monitor, Header = "Start with Windows", Description = "Starts quietly in the tray, so your phone can sync whenever this PC is on.", Content = Toggle(Tray.StartsWithWindows, Tray.SetStartWithWindows) });

        folderCard.Content = Action("Change…", primary: false, () => _ = ChangeBackupFolderAsync());
        backupNowCard.Content = Action("Back up now", primary: false, () => _ = BackupNowAsync());
        driveCard.Content = Toggle(Services.Settings.BackupDrive, on => { Services.Settings.BackupDrive = on; Services.Settings.Save(); _ = RefreshAsync(); });
        var keepCount = new NumberBox { Value = Services.Settings.AutoBackupKeep, Minimum = 2, Maximum = 50, MinWidth = 130, SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact };
        keepCount.ValueChanged += (_, e) =>
        {
            if (!double.IsNaN(e.NewValue))
            {
                Services.Settings.AutoBackupKeep = (int)Math.Clamp(e.NewValue, 2, 50);
                Services.Settings.Save();
            }
        };
        var daily = new SettingsExpander
        {
            Icon = Icons.Clock,
            Header = "Daily backup",
            Description = "Runs once a day while Tsuzuki is open. Your phone can restore them too.",
            Content = Toggle(Services.Settings.AutoBackup, on => { Services.Settings.AutoBackup = on; Services.Settings.Save(); }),
        };
        daily.Add(new SettingsCard { Header = "Keep backups", Description = "How many recent backups to keep in the backup folder.", Content = keepCount });
        AddGroup("Backups",
            folderCard,
            backupNowCard,
            driveCard,
            daily,
            new SettingsCard { Icon = Icons.RotateCcw, Header = "Restore a backup", Description = "Works with backups from this PC, Tsuzuki, Mihon, Komikku and Neko.", Content = Action("Restore…", primary: false, () => _ = RestoreAsync()) });

        AddGroup("Layout lab",
            new SettingsCard { Icon = Icons.Sparkles, Header = "Motion: Fluent timings", Description = "Fluent navigation rhythm: 250 ms drill-in, back always the fastest leg, an entrance rise instead of page zoom, springy chrome and sheets you can interrupt mid-flight.", Content = Toggle(Services.Settings.LabMotion, on => { Services.Settings.LabMotion = on; Services.Settings.Save(); }) },
            new SettingsCard { Icon = Icons.List, Header = "Lists: animate changes", Description = "Library, browse and history lists animate items being added, removed and reordered.", Content = Toggle(Services.Settings.LabLists, on => { Services.Settings.LabLists = on; Services.Settings.Save(); }) },
            new SettingsCard { Icon = Icons.Loader, Header = "Skeletons: pulse", Description = "Loading placeholders breathe with the same travelling shimmer as Android. New placeholders only.", Content = Toggle(Services.Settings.LabSkeletons, on => { Services.Settings.LabSkeletons = on; Services.Settings.Save(); }) });

        Button? checkNow = null;
        checkNow = Action("Check now", primary: false, async () =>
        {
            checkNow!.IsEnabled = false;
            try
            {
                await AppUpdates.CheckNowAsync();
            }
            finally
            {
                checkNow.IsEnabled = true;
            }
        });
        var version = typeof(SettingsPage).Assembly.GetName().Version?.ToString(3) ?? "0.1.0";
        dataFolderCard.Content = Action("Open folder", primary: false, () =>
        {
            if (Services.Engine is { } engine)
            {
                _ = Windows.System.Launcher.LaunchFolderPathAsync(engine.Paths.Root);
            }
        });
        var about = new SettingsExpander
        {
            Icon = Icons.Info,
            Header = "Tsuzuki for Windows",
            Description = "Reads MangaDex natively and keeps your phone in sync.",
            Content = new TextBlock { Text = "Version " + version, Style = Res<Style>("TzCaption"), IsTextSelectionEnabled = true },
        };
        about.Add(dataFolderCard);
        about.Add(new SettingsCard { Header = "Source code", Description = "Tsuzuki is open source. Releases and changelogs live on GitHub.", Content = Links(("GitHub", WindowsUpdateSource.RepoUrl), ("Releases", WindowsUpdateSource.RepoUrl + "/releases")) });
        about.Add(new SettingsCard { Header = "MangaDex", Description = "Covers and pages come from MangaDex; Tsuzuki is not affiliated with it.", Content = Links(("mangadex.org", "https://mangadex.org")) });
        about.Add(new SettingsCard { Header = "Open source", Description = "Icons by Lucide (ISC). Built with the Windows App SDK.", Content = Links(("Lucide", "https://lucide.dev")) });
        AddGroup("About",
            new SettingsCard { Icon = Icons.Download, Header = "Check for updates", Description = "Looks for new Windows builds on GitHub at startup and once a day. You decide when to restart.", Content = Toggle(Services.Settings.CheckForUpdates, on => { Services.Settings.CheckForUpdates = on; Services.Settings.Save(); }) },
            new SettingsCard { Icon = Icons.Refresh, Header = "Update now", Description = "Looks for a newer build right away and downloads it.", Content = checkNow },
            about);
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    private static Button Action(string label, bool primary, Action onClick)
    {
        var button = new Button { Content = label, MinWidth = 96 };
        if (primary)
        {
            button.Style = Res<Style>("AccentButtonStyle");
        }
        button.Click += (_, _) => onClick();
        return button;
    }

    private static ToggleSwitch Toggle(bool isOn, Action<bool> onToggle)
    {
        var toggle = new ToggleSwitch { IsOn = isOn, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        toggle.Toggled += (_, _) => onToggle(toggle.IsOn);
        return toggle;
    }

    private static ComboBox Choice(string[] labels, int selected, Action<int> onSelect)
    {
        var combo = new ComboBox { ItemsSource = labels, SelectedIndex = selected, MinWidth = 160 };
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedIndex >= 0)
            {
                onSelect(combo.SelectedIndex);
            }
        };
        return combo;
    }

    private static StackPanel Links(params (string Label, string Url)[] links)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
        foreach (var (label, url) in links)
        {
            panel.Children.Add(new HyperlinkButton { Content = label, NavigateUri = new Uri(url) });
        }
        return panel;
    }

    private void AddGroup(string title, params UIElement[] cards)
    {
        var group = new StackPanel { Spacing = 4 };
        group.Children.Add(new TextBlock { Text = title, Style = Res<Style>("TzBodyStrong"), Margin = new Thickness(1, 0, 0, 4) });
        foreach (var card in cards)
        {
            group.Children.Add(card);
        }
        body.Children.Add(group);
    }

    private static Slider CoverSlider()
    {
        var slider = new Slider { Minimum = 130, Maximum = 230, StepFrequency = 10, Value = Services.Settings.TileWidth, Width = 220 };
        slider.ValueChanged += (_, e) =>
        {
            Services.Settings.TileWidth = e.NewValue;
            Services.Settings.Save();
            AppHost.TileSizeChanged();
        };
        return slider;
    }

    public void OnShown() => _ = RefreshAsync();

    private async Task RefreshAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var devices = await engine.Hub.GetDevicesAsync();
        var hub = engine.Hub.Status;
        syncCard.Description = !hub.Running
            ? "Sync is off: " + (hub.Error ?? "the hub isn't running.")
            : devices.Count == 0
                ? "No phone paired yet."
                : string.Join(" · ", devices.OrderByDescending(d => d.LastSeenAt).Select(d => $"{d.Name}, synced {Format.Relative(d.LastSeenAt)}"));
        var target = await AppHost.BackupTargetAsync();
        folderCard.Icon = target?.Id == "drive" ? Icons.Cloud : Icons.HardDrive;
        folderCard.Description = target is null ? "No backup folder available." : $"{target.DisplayName} · {target.Location}";
        backupNowCard.Description = Services.Settings.LastAutoBackup == default ? "No backup yet." : "Last backup " + Format.Relative(Services.Settings.LastAutoBackup.ToUnixTimeMilliseconds());
        driveCard.Visibility = await AppHost.DriveBackupAvailableAsync() ? Visibility.Visible : Visibility.Collapsed;
        dataFolderCard.Description = engine.Paths.Root;
        downloadFolderCard.Description = engine.Downloads.Options.Root + " · Mihon-compatible: folders copied from a phone are picked up.";
        var categories = await engine.Library.GetCategoriesAsync();
        var chosen = categories.Where(c => Services.Settings.DownloadsAutoCategories.Contains(c.Uid)).Select(c => c.Name).ToList();
        autoDownloadCard.Description = chosen.Count == 0 ? "Off. Choose categories whose new chapters download on their own." : "For " + string.Join(", ", chosen);
        readerMode.SelectedIndex = (int)Services.Settings.ReaderMode;
        readerDirection.SelectedIndex = Services.Settings.RightToLeft ? 0 : 1;
    }

    private async Task BackupNowAsync()
    {
        await BackupAsync();
        await RefreshAsync();
    }

    public static async Task BackupAsync()
    {
        if (Services.Engine is not { } engine || await AppHost.BackupTargetAsync() is not { } target)
        {
            return;
        }
        try
        {
            var result = await engine.Backup.CreateAsync(target);
            Services.Settings.LastAutoBackup = DateTimeOffset.Now;
            Services.Settings.Save();
            try
            {
                await target.PruneAsync(Math.Max(1, Services.Settings.AutoBackupKeep));
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException)
            {
                Toasts.Show("Old backups were not cleaned up", e.Message, Icons.Alert);
            }
            Toasts.Show("Backed up", $"{result.MangaCount} titles · {target.DisplayName}", Icons.CircleCheck);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Toasts.Show("Backup failed", e.Message, Icons.Alert);
        }
    }

    private async Task ChangeDownloadFolderAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.DocumentsLibrary };
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow));
        var folder = await picker.PickSingleFolderAsync();
        if (folder is null || string.Equals(folder.Path, engine.Downloads.Options.Root, StringComparison.OrdinalIgnoreCase))
        {
            return;
        }
        downloadFolderCard.Description = "Moving downloads…";
        var progress = new Progress<double>(p => downloadFolderCard.Description = $"Moving downloads… {p:P0}");
        try
        {
            await engine.Downloads.MoveRootAsync(folder.Path, progress);
            Services.Settings.DownloadsRoot = folder.Path;
            Services.Settings.Save();
            Toasts.Show("Downloads moved", folder.Path, Icons.Folder);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Toasts.Show("Couldn't move downloads", e.Message, Icons.Alert);
        }
        await RefreshAsync();
    }

    private async Task ChooseAutoDownloadAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var categories = await engine.Library.GetCategoriesAsync();
        if (categories.Count == 0)
        {
            Toasts.Show("No categories yet", "Create categories on your phone or in the library first.", Icons.Info);
            return;
        }
        var panel = new StackPanel { Spacing = 4 };
        var boxes = categories.Select(c => new CheckBox { Content = c.Name, IsChecked = Services.Settings.DownloadsAutoCategories.Contains(c.Uid), Tag = c.Uid }).ToList();
        foreach (var box in boxes)
        {
            panel.Children.Add(box);
        }
        var dialog = new ContentDialog
        {
            Title = "Download new chapters automatically",
            Content = new ScrollViewer { Content = panel, MaxHeight = 360 },
            PrimaryButtonText = "Save",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = XamlRoot,
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary)
        {
            return;
        }
        Services.Settings.DownloadsAutoCategories = [.. boxes.Where(b => b.IsChecked == true).Select(b => (long)b.Tag)];
        Services.Settings.Save();
        AppHost.ApplySourceOptions();
        await RefreshAsync();
    }

    private async Task ChangeBackupFolderAsync()
    {
        var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.DocumentsLibrary };
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow));
        var folder = await picker.PickSingleFolderAsync();
        if (folder is null)
        {
            return;
        }
        Services.Settings.BackupFolder = folder.Path == AppPaths.Default.Backups ? null : folder.Path;
        Services.Settings.Save();
        await RefreshAsync();
    }

    public static async Task RestoreAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var picker = new FileOpenPicker { SuggestedStartLocation = PickerLocationId.DocumentsLibrary };
        picker.FileTypeFilter.Add(".tachibk");
        picker.FileTypeFilter.Add(".gz");
        picker.FileTypeFilter.Add(".proto");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow));
        var file = await picker.PickSingleFileAsync();
        if (file is null)
        {
            return;
        }
        if (!await Dialogs.ConfirmAsync("Restore this backup?",
                $"{file.Name} is merged into your library: titles, categories and reading progress that are newer in the backup are brought in. Nothing newer on this PC is overwritten, and nothing is deleted.",
                "Restore"))
        {
            return;
        }
        try
        {
            await using var stream = File.OpenRead(file.Path);
            var result = await engine.Backup.RestoreAsync(stream);
            AppInfoBars.Show("backup-restored", InfoBarSeverity.Success,
                $"Backup restored. {result.MangaAdded + result.MangaUpdated:N0} titles",
                $"{result.MangaAdded:N0} new · {result.ChaptersUpdated:N0} chapters updated from {file.Name}");
            await App.MainWindow.NavigateRootAsync("library");
        }
        catch (Exception e) when (e is IOException or InvalidDataException or InvalidOperationException or Google.Protobuf.InvalidProtocolBufferException)
        {
            Toasts.Show("Couldn't restore that file", "It doesn't look like a Tsuzuki or Mihon backup.", Icons.Alert);
        }
    }
}
