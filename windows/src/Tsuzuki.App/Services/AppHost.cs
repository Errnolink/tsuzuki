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
    private static DateTimeOffset lastAuthRejection;
    private static bool hubAttempted;
    private static bool restartingHub;
    private static Tray? tray;
    private static bool quitting;

    public static event EventHandler? TilesResized;
    public static event EventHandler? EngineReady;

    public static async Task StartAsync(MainWindow window)
    {
        var paths = DevHarness.ResolvePaths();
        paths.EnsureCreated();
        var crashed = Log.BeginSession();
        Log.Trail($"start {typeof(AppHost).Assembly.GetName().Version}");
        ApplyTheme();
        window.RestoreWindowBounds();
        window.SetSyncState(SyncChipState.Starting, "Starting", "starting", "Starting sync");
        BuildShell(window);
        var shell = window.NavigateRootAsync(Environment.GetCommandLineArgs().Contains(OpenSettingsArg) ? "settings" : StartPage);

        Engine engine;
        while (true)
        {
            try
            {
                engine = await Task.Run(() => EngineFactory.OpenAsync(paths, CurrentOptions()));
                break;
            }
            catch (Exception e)
            {
                window.SetSyncState(SyncChipState.Off, "Offline", "off, your library couldn't open", "Library couldn't open");
                if (!await Dialogs.ConfirmAsync("Your library couldn't open", $"{e.Message}", "Retry"))
                {
                    await PrepareExitAsync();
                    Application.Current.Exit();
                    return;
                }
            }
        }
        Controls.Thumbnails.Configure(Path.Combine(engine.Paths.Root, "cache", "thumbs"));
        Services.Engine = engine;
        RaiseEngineReady();
        _ = Log.Guard("downloads-start", engine.Downloads.StartAsync);
        engine.Tracking.UpdateAfterReading = Services.Settings.TrackingUpdateAfterReading;
        _ = Log.Guard("tracking-drain", () => engine.Tracking.DrainAsync());
        trackingTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(10) };
        trackingTimer.Tick += (_, _) => _ = Log.Guard("tracking-drain", () => engine.Tracking.DrainAsync());
        trackingTimer.Start();
        engine.Downloads.BackgroundError += (_, e) => Log.Error("downloads", e);
        Windows.Networking.Connectivity.NetworkInformation.NetworkStatusChanged += _ => engine.Downloads.NetworkChanged();
        if (!Services.Settings.BackupDriveChosen)
        {
            Services.Settings.BackupDrive = Services.Settings.LastAutoBackup != default && await DriveBackupAvailableAsync();
            Services.Settings.BackupDriveChosen = true;
            Services.Settings.Save();
        }

        engine.Library.Changed += (_, e) => window.DispatcherQueue.TryEnqueue(() => OnLibraryChanged(e));
        engine.Hub.DeviceSynced += (_, e) => window.DispatcherQueue.TryEnqueue(() =>
        {
            lastPhoneSync = DateTimeOffset.Now;
            lastPhoneName = e.Device.Name;
            AppInfoBars.Dismiss("sync-auth");
            UpdateSyncChip();
        });
        engine.Hub.StatusChanged += (_, _) => window.DispatcherQueue.TryEnqueue(() =>
        {
            UpdateSyncChip();
            UpdateHubInfoBar();
        });
        engine.Hub.AuthRejected += (_, _) => window.DispatcherQueue.TryEnqueue(OnAuthRejected);

        if (!DevHarness.Active)
        {
            tray = new Tray(WinRT.Interop.WindowNative.GetWindowHandle(window), Path.Combine(AppContext.BaseDirectory, "Assets", "Tsuzuki.ico"));
            tray.OpenRequested += (_, _) => window.DispatcherQueue.TryEnqueue(window.BringToFront);
            tray.QuitRequested += (_, _) => window.DispatcherQueue.TryEnqueue(() => _ = QuitAsync());
            tray.Show($"{Tsuzuki.Core.AppIdentity.Name} · phone sync is on");
        }

        if (DevHarness.Current is not { NoHub: true })
        {
            try
            {
                await engine.Hub.StartAsync();
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                Log.Error("hub", e);
            }
        }

        await shell;
        hubAttempted = true;
        var devices = await engine.Hub.GetDevicesAsync();
        if (devices.Count > 0)
        {
            var latest = devices.MaxBy(d => d.LastSeenAt)!;
            lastPhoneSync = DateTimeOffset.FromUnixTimeMilliseconds(latest.LastSeenAt);
            lastPhoneName = latest.Name;
        }
        UpdateSyncChip();
        UpdateHubInfoBar();
        if (crashed is not null && !DevHarness.Active)
        {
            AppInfoBars.Show(
                "crashed",
                Microsoft.UI.Xaml.Controls.InfoBarSeverity.Warning,
                "Tsuzuki closed unexpectedly last time",
                "The last steps before it closed are saved in the log folder. Sending them helps fix it.",
                "Open log folder",
                () => _ = Windows.System.Launcher.LaunchFolderPathAsync(paths.Logs));
        }

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
        Log.Trail("exit");
        await Reader.ReaderPage.CloseCurrentAsync();
        if (Services.Engine is { } engine)
        {
            await engine.Hub.StopAsync();
            await engine.Extensions.DisposeAsync();
            await engine.Downloads.StopAsync();
            await engine.Library.FlushAsync();
        }
        tray?.Dispose();
        Log.EndSession();
        tray = null;
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public static void ApplyTheme()
    {
        var theme = ResolvedTheme();
        Design.Theme.Apply(theme);
        App.MainWindow.SetTheme(theme);
    }

    private static ElementTheme ResolvedTheme() => Services.Settings.Theme switch
    {
        ThemeChoice.Light => ElementTheme.Light,
        ThemeChoice.Dark => ElementTheme.Dark,
        _ => new Windows.UI.ViewManagement.UISettings().GetColorValue(Windows.UI.ViewManagement.UIColorType.Background).R > 128 ? ElementTheme.Light : ElementTheme.Dark,
    };

    public static async Task SwitchThemeAsync()
    {
        var running = Application.Current.RequestedTheme == ApplicationTheme.Light ? ElementTheme.Light : ElementTheme.Dark;
        if (ResolvedTheme() == running)
        {
            ApplyTheme();
            return;
        }
        await RestartIntoSettingsAsync();
    }

    public const string StartPage = "home";

    private static async Task RestartIntoSettingsAsync()
    {
        await PrepareExitAsync();
        var args = Environment.GetCommandLineArgs().Skip(1)
            .Where(a => a != OpenSettingsArg && !a.Equals("--background", StringComparison.OrdinalIgnoreCase))
            .Append(OpenSettingsArg);
        var failure = Microsoft.Windows.AppLifecycle.AppInstance.Restart(string.Join(' ', args.Select(a => a.Contains(' ') ? $"\"{a}\"" : a)));
        Log.Error("restart", new InvalidOperationException("restart failed: " + failure));
        await Dialogs.ConfirmAsync("Tsuzuki needs a restart", "Open Tsuzuki again to finish applying the change.", "Close Tsuzuki");
        Application.Current.Exit();
    }

    private const string OpenSettingsArg = "--open-settings";

    public static void ApplySourceOptions()
    {
        if (Services.Engine is { } engine)
        {
            EngineFactory.ApplyOptions(engine, CurrentOptions());
        }
    }

    public static EngineOptions CurrentOptions() => new(
        Services.Settings.DataSaver,
        new Tsuzuki.Core.Downloads.DownloadOptions
        {
            Root = string.IsNullOrWhiteSpace(Services.Settings.DownloadsRoot) ? Tsuzuki.Core.Downloads.DownloadQueue.DefaultRoot : Services.Settings.DownloadsRoot,
            UnmeteredOnly = Services.Settings.DownloadsUnmeteredOnly,
            DeleteAfterReadKeep = Services.Settings.DownloadsDeleteAfterRead,
            AutoDownloadCategories = Services.Settings.DownloadsAutoCategories.ToHashSet(),
        },
        IsMetered,
        Credentials);

    private static readonly VaultCredentials Credentials = new();
    private static DispatcherTimer? trackingTimer;

    private static bool IsMetered()
    {
        try
        {
            return Windows.Networking.Connectivity.NetworkInformation.GetInternetConnectionProfile()?.GetConnectionCost() is { } cost
                && cost.NetworkCostType is Windows.Networking.Connectivity.NetworkCostType.Fixed or Windows.Networking.Connectivity.NetworkCostType.Variable;
        }
        catch (Exception)
        {
            return false;
        }
    }

    public static void TileSizeChanged() => TilesResized?.Invoke(null, EventArgs.Empty);

    private static void RaiseEngineReady() => EngineReady?.Invoke(null, EventArgs.Empty);

    private static void BuildShell(MainWindow window)
    {
        window.AddRootPage("home", () => new Next.HomePage());
        window.AddRootPage("library", () => new Next.LibraryPage());
        window.AddRootPage("updates", () => new Next.UpdatesPage());
        window.AddRootPage("history", () => new Next.HistoryPage());
        window.AddRootPage("browse", () => new BrowsePage());
        window.AddRootPage("extensions", () => new ExtensionsPage());
        window.AddRootPage("downloads", () => new DownloadsPage());
        window.AddRootPage("settings", () => new SettingsPage());
        Theme.Changed += (_, _) => UpdateSyncChip();
        chipTimer = window.DispatcherQueue.CreateTimer();
        chipTimer.Interval = TimeSpan.FromSeconds(30);
        chipTimer.Tick += (_, _) => UpdateSyncChip();
        chipTimer.Start();
        window.AppWindow.Closing += (sender, e) =>
        {
            try
            {
                window.SaveWindowBounds();
            }
            catch (Exception error)
            {
                Log.Error("save-bounds", error);
            }
            if (quitting)
            {
                return;
            }
            e.Cancel = true;
            if (DevHarness.Active || !Services.Settings.KeepSyncing || tray is null)
            {
                _ = Log.Guard("quit", QuitAsync);
                return;
            }
            window.AppWindow.Hide();
            Log.Trail("hidden to tray");
            if (!Services.Settings.TrayHintShown)
            {
                Services.Settings.TrayHintShown = true;
                Services.Settings.Save();
                tray.Balloon("Tsuzuki is still syncing", "Your phone can keep syncing while the window is closed. Quit from this icon.");
            }
        };
    }

    public static async Task<IBackupTarget?> BackupTargetAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return null;
        }
        if (Services.Settings.BackupFolder is { } folder && await Task.Run(() => Directory.Exists(folder)))
        {
            return new LocalFolderTarget(folder);
        }
        var targets = await engine.Backup.DiscoverTargetsAsync();
        if (Services.Settings.BackupDrive)
        {
            return targets.FirstOrDefault(t => t.Id == "drive") ?? targets.FirstOrDefault();
        }
        return targets.FirstOrDefault(t => t.Id != "drive") ?? targets.FirstOrDefault();
    }

    public static async Task<bool> DriveBackupAvailableAsync() =>
        Services.Engine is { } engine && (await engine.Backup.DiscoverTargetsAsync()).Any(t => t.Id == "drive");

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
        try
        {
            if (!await engine.Library.HasFavouritesAsync() || await BackupTargetAsync() is not { } target)
            {
                return;
            }
            await engine.Backup.CreateAsync(target);
            Services.Settings.LastAutoBackup = DateTimeOffset.Now;
            Services.Settings.Save();
            try
            {
                await target.PruneAsync(Math.Max(1, Services.Settings.AutoBackupKeep));
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                Log.Error("backup prune", e);
                Toasts.Show("Old backups were not cleaned up", e.Message, Icons.Alert);
            }
        }
        catch (Exception e) when (e is not OperationCanceledException)
        {
            Log.Error("auto backup", e);
            Toasts.Show("Daily backup failed", e.Message, Icons.Alert);
        }
    }

    private static void UpdateSyncChip()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var window = App.MainWindow;
        var status = engine.Hub.Status;
        if (!status.Running)
        {
            if (status.Error is not null)
            {
                window.SetSyncState(SyncChipState.Error, "Sync error", "error, the hub couldn't start: " + status.Error, "Hub error");
            }
            else if (hubAttempted && !restartingHub)
            {
                window.SetSyncState(SyncChipState.Off, "Sync off", "off, the hub isn't running", "Hub off");
            }
            else
            {
                window.SetSyncState(SyncChipState.Starting, "Starting", "starting", "Starting sync");
            }
            return;
        }
        if (lastPhoneName is null)
        {
            window.SetSyncState(SyncChipState.Unpaired, "Pair your phone", "on, no phone paired yet", "Hub on · no phone paired");
            return;
        }
        var ago = Format.Relative(lastPhoneSync.ToUnixTimeMilliseconds());
        window.SetSyncState(SyncChipState.Synced, "Synced " + ago, $"on, {lastPhoneName} synced {ago}", $"{lastPhoneName} synced {ago}");
    }

    private static void UpdateHubInfoBar(bool afterRestart = false)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var status = engine.Hub.Status;
        if (status.Running)
        {
            AppInfoBars.Dismiss("sync-hub");
            return;
        }
        if (status.Error is null)
        {
            return;
        }
        var prefix = afterRestart ? "Restarting didn't help. " : string.Empty;
        AppInfoBars.Show("sync-hub", Microsoft.UI.Xaml.Controls.InfoBarSeverity.Error,
            "Sync is off: the hub isn't running",
            $"{prefix}{status.Error.TrimEnd('.')}. Another app may be using the sync port. Close it, then restart sync.",
            "Restart sync", () => _ = RestartSyncAsync());
    }

    private static async Task RestartSyncAsync()
    {
        if (Services.Engine is not { } engine || restartingHub)
        {
            return;
        }
        restartingHub = true;
        UpdateSyncChip();
        try
        {
            await engine.Hub.StopAsync();
            await engine.Hub.StartAsync();
        }
        catch (Exception e) when (e is IOException or InvalidOperationException or System.Net.Sockets.SocketException)
        {
            Log.Error("hub", e);
        }
        finally
        {
            restartingHub = false;
        }
        UpdateSyncChip();
        UpdateHubInfoBar(afterRestart: true);
    }

    private static void OnAuthRejected()
    {
        var now = DateTimeOffset.Now;
        var quiet = now - lastAuthRejection > TimeSpan.FromMinutes(2);
        lastAuthRejection = now;
        if (!quiet)
        {
            return;
        }
        AppInfoBars.Show("sync-auth", Microsoft.UI.Xaml.Controls.InfoBarSeverity.Warning,
            "A phone couldn't sync",
            "It's using an old pairing key, so this PC turned it away. Pair it again to keep syncing.",
            "Pair again", DevicesPanel.Show);
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
                action: lead => _ = AppNav.OpenReaderAsync(manga.MangaId, chapter.ChapterId, page, lead),
                coverUrl: manga.ThumbnailUrl,
                seconds: 9);
        }
    }
}
