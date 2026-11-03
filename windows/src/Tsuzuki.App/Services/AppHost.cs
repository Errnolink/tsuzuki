using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Pages;
using Tsuzuki.Core;
using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sync;

namespace Tsuzuki.App;

public static class AppHost
{
    private static DispatcherQueueTimer? chipTimer;
    private static DispatcherQueueTimer? autoBackupTimer;
    private static DateTimeOffset lastPhoneSync;
    private static string? lastPhoneName;
    private static Tray? tray;
    private static bool quitting;

    public static event EventHandler? TilesResized;
    public static event EventHandler? EngineReady;

    public static async Task StartAsync(MainWindow window)
    {
        var paths = DevHarness.ResolvePaths();
        paths.EnsureCreated();
        Services.Settings = AppSettings.Load(paths.Settings);
        DevHarness.ApplyThemeOverride();
        ApplyTheme();
        window.RestoreWindowBounds();
        window.SetSyncState("Starting", Design.Theme.Brush("TzTextTertiary"), pulse: false);
        BuildShell(window);
        var shell = window.NavigateRootAsync("library");

        Engine engine;
        while (true)
        {
            try
            {
                engine = await Task.Run(() => EngineFactory.OpenAsync(paths, new EngineOptions(Services.Settings.DataSaver)));
                break;
            }
            catch (Exception e)
            {
                window.SetSyncState("Offline", Design.Theme.Brush("TzTextTertiary"), pulse: false);
                if (!await Dialogs.ConfirmAsync("Your library couldn't open", $"{e.Message}", "Retry"))
                {
                    await PrepareExitAsync();
                    Application.Current.Exit();
                    return;
                }
            }
        }
        Services.Engine = engine;
        RaiseEngineReady();
        if (!Services.Settings.BackupDriveChosen)
        {
            Services.Settings.BackupDrive = Services.Settings.LastAutoBackup != default && DriveBackupAvailable();
            Services.Settings.BackupDriveChosen = true;
            Services.Settings.Save();
        }

        engine.Library.Changed += (_, e) => window.DispatcherQueue.TryEnqueue(() => OnLibraryChanged(e));
        engine.Hub.DeviceSynced += (_, e) => window.DispatcherQueue.TryEnqueue(() =>
        {
            lastPhoneSync = DateTimeOffset.Now;
            lastPhoneName = e.Device.Name;
            UpdateSyncChip(pulse: true);
        });
        engine.Hub.StatusChanged += (_, _) => window.DispatcherQueue.TryEnqueue(() => UpdateSyncChip(pulse: false));

        if (!DevHarness.Active)
        {
            tray = new Tray(WinRT.Interop.WindowNative.GetWindowHandle(window), Path.Combine(AppContext.BaseDirectory, "Assets", "Tsuzuki.ico"));
            tray.OpenRequested += (_, _) => window.DispatcherQueue.TryEnqueue(window.BringToFront);
            tray.QuitRequested += (_, _) => window.DispatcherQueue.TryEnqueue(() => _ = QuitAsync());
            tray.Show("Tsuzuki · phone sync is on");
        }

        await shell;

        if (DevHarness.Current is not { NoHub: true })
        {
            try
            {
                await engine.Hub.StartAsync();
            }
            catch (Exception e) when (e is IOException or InvalidOperationException or System.Net.Sockets.SocketException)
            {
                Toasts.Show("Phone sync couldn't start", e.Message, Icons.Alert);
            }
        }
        var devices = await engine.Hub.GetDevicesAsync();
        if (devices.Count > 0)
        {
            var latest = devices.MaxBy(d => d.LastSeenAt)!;
            lastPhoneSync = DateTimeOffset.FromUnixTimeMilliseconds(latest.LastSeenAt);
            lastPhoneName = latest.Name;
        }
        UpdateSyncChip(pulse: false);

        if (!Services.Settings.OnboardingDone && (DevHarness.Current is null || DevHarness.Current.Sheet == "onboarding"))
        {
            Onboarding.Show();
        }
        if (!DevHarness.Active)
        {
            _ = AutoBackupAsync();
            AppUpdates.Start(window);
            autoBackupTimer = window.DispatcherQueue.CreateTimer();
            autoBackupTimer.Interval = TimeSpan.FromHours(1);
            autoBackupTimer.Tick += (_, _) => _ = AutoBackupAsync();
            autoBackupTimer.Start();
        }
        if (DevHarness.Current is { CycleThemeLater: true } harness)
        {
            DevHarness.StartThemeCycle(window, harness.CycleThemeDelay);
        }
        await DevHarness.RunAsync();
    }

    public static async Task QuitAsync()
    {
        if (quitting)
        {
            return;
        }
        quitting = true;
        await PrepareExitAsync();
        Application.Current.Exit();
    }

    public static async Task PrepareExitAsync()
    {
        await Reader.ReaderPage.CloseCurrentAsync();
        if (Services.Engine is { } engine)
        {
            await engine.Hub.StopAsync();
            await engine.Library.FlushAsync();
        }
        tray?.Dispose();
        tray = null;
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public static void ApplyTheme()
    {
        var theme = Services.Settings.Theme switch
        {
            ThemeChoice.Light => ElementTheme.Light,
            ThemeChoice.Dark => ElementTheme.Dark,
            _ => Application.Current.RequestedTheme == ApplicationTheme.Light ? ElementTheme.Light : ElementTheme.Dark,
        };
        Design.Theme.Apply(theme);
        App.MainWindow.SetTheme(theme);
    }

    public static void ApplySourceOptions()
    {
        if (Services.Engine is { } engine)
        {
            EngineFactory.ApplyOptions(engine, new EngineOptions(Services.Settings.DataSaver));
        }
    }

    public static void TileSizeChanged() => TilesResized?.Invoke(null, EventArgs.Empty);

    private static void RaiseEngineReady() => EngineReady?.Invoke(null, EventArgs.Empty);

    private static void BuildShell(MainWindow window)
    {
        window.AddRailItem("library", Icons.Library, "Library", () => new LibraryPage());
        window.AddRailItem("updates", Icons.Bell, "Updates", () => new ListPage(ListKind.Updates));
        window.AddRailItem("history", Icons.History, "History", () => new ListPage(ListKind.History));
        window.AddRailItem("browse", Icons.Compass, "Browse", () => new BrowsePage());
        window.AddRailItem("settings", Icons.Settings, "Settings", () => new SettingsPage(), footer: true);
        window.SyncChipClicked += (_, _) => DevicesPanel.Show();
        window.SearchSubmitted += async (_, text) =>
        {
            if (text.Length == 0) return;
            await window.NavigateRootAsync("browse");
            if (window.Nav.Root("browse") is Pages.BrowsePage browse)
            {
                browse.Search(text);
            }
        };
        Theme.Changed += (_, _) => UpdateSyncChip(pulse: false);
        chipTimer = window.DispatcherQueue.CreateTimer();
        chipTimer.Interval = TimeSpan.FromSeconds(30);
        chipTimer.Tick += (_, _) => UpdateSyncChip(pulse: false);
        chipTimer.Start();
        window.AppWindow.Closing += (sender, e) =>
        {
            window.SaveWindowBounds();
            if (quitting || DevHarness.Active)
            {
                if (!quitting)
                {
                    e.Cancel = true;
                    _ = QuitAsync();
                }
                return;
            }
            if (!Services.Settings.KeepSyncing)
            {
                e.Cancel = true;
                _ = QuitAsync();
                return;
            }
            e.Cancel = true;
            window.AppWindow.Hide();
            if (!Services.Settings.TrayHintShown)
            {
                Services.Settings.TrayHintShown = true;
                Services.Settings.Save();
                tray?.Balloon("Tsuzuki is still syncing", "Your phone can keep syncing while the window is closed. Quit from this icon.");
            }
        };
    }

    public static IBackupTarget? BackupTarget()
    {
        if (Services.Engine is not { } engine)
        {
            return null;
        }
        if (Services.Settings.BackupFolder is { } folder && Directory.Exists(folder))
        {
            return new LocalFolderTarget(folder);
        }
        var targets = engine.Backup.DiscoverTargets();
        if (Services.Settings.BackupDrive)
        {
            return targets.FirstOrDefault(t => t.Id == "drive") ?? targets.FirstOrDefault();
        }
        return targets.FirstOrDefault(t => t.Id != "drive") ?? targets.FirstOrDefault();
    }

    public static bool DriveBackupAvailable() =>
        Services.Engine?.Backup.DiscoverTargets().Any(t => t.Id == "drive") == true;

    private static async Task AutoBackupAsync()
    {
        if (!Services.Settings.AutoBackup || Services.Engine is not { } engine)
        {
            return;
        }
        if (DateTimeOffset.Now - Services.Settings.LastAutoBackup < TimeSpan.FromHours(24))
        {
            return;
        }
        var library = await engine.Library.GetLibraryAsync();
        if (library.Count == 0 || BackupTarget() is not { } target)
        {
            return;
        }
        try
        {
            await engine.Backup.CreateAsync(target);
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
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Toasts.Show("Daily backup failed", e.Message, Icons.Alert);
        }
    }

    private static void UpdateSyncChip(bool pulse)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var window = App.MainWindow;
        var status = engine.Hub.Status;
        if (!status.Running)
        {
            window.SetSyncState(status.Error is null ? "Sync off" : "Sync error", Design.Theme.Brush("TzDanger"), pulse: false);
            return;
        }
        if (lastPhoneName is null)
        {
            window.SetSyncState("Pair your phone", Design.Theme.Brush("TzTextTertiary"), pulse: false);
            return;
        }
        var ago = Format.Relative(lastPhoneSync.ToUnixTimeMilliseconds());
        var fresh = DateTimeOffset.Now - lastPhoneSync < TimeSpan.FromMinutes(30);
        window.SetSyncState($"{lastPhoneName} · {ago}", Design.Theme.Brush(fresh ? "TzSuccess" : "TzTextTertiary"), pulse);
    }

    private static void OnLibraryChanged(LibraryChangedEventArgs e)
    {
        if (e.Origin != ChangeOrigin.Remote || e.Handoffs.Count == 0 || Services.Engine is not { } engine)
        {
            return;
        }
        if (e.Handoffs.Count > 3)
        {
            Toasts.Show($"Synced from {e.DeviceName ?? "your phone"}", $"{e.MangaIds.Count} titles updated", Icons.Smartphone);
            return;
        }
        var handoff = e.Handoffs[^1];
        _ = ShowHandoffAsync(engine, handoff);
    }

    private static async Task ShowHandoffAsync(Engine engine, ProgressHandoff handoff)
    {
        var manga = await engine.Library.GetMangaAsync(handoff.MangaId);
        var chapter = await engine.Library.GetChapterAsync(handoff.ChapterId);
        if (manga is null || chapter is null)
        {
            return;
        }
        var number = chapter.ChapterNumber >= 0 ? "Ch. " + Format.ChapterNumber(chapter.ChapterNumber) : chapter.Name;
        var window = App.MainWindow;
        var isHidden = !window.AppWindow.IsVisible;

        if (handoff.Read)
        {
            if (isHidden && tray is not null)
            {
                tray.Balloon($"Finished on {handoff.DeviceName}", $"{manga.Title} · {number}");
            }
            else
            {
                Toasts.Show($"Finished on {handoff.DeviceName}", $"{manga.Title} · {number}", coverUrl: manga.ThumbnailUrl);
            }
            return;
        }
        var page = handoff.LastPageRead;
        if (isHidden && tray is not null)
        {
            tray.Balloon($"Continue from {handoff.DeviceName}", $"{manga.Title} · {number} · page {page + 1}");
        }
        else
        {
            Toasts.Show(
                $"Continue from {handoff.DeviceName}",
                $"{manga.Title} · {number} · page {page + 1}",
                actionLabel: "Continue",
                action: () => _ = AppNav.OpenReaderAsync(manga.MangaId, chapter.ChapterId, page, null),
                coverUrl: manga.ThumbnailUrl,
                seconds: 9);
        }
    }
}
