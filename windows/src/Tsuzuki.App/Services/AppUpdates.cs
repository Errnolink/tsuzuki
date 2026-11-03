using Microsoft.UI.Dispatching;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Updates;
using Velopack;

namespace Tsuzuki.App;

public enum UpdateCheck
{
    NotInstalled,
    UpToDate,
    Ready,
    Failed,
}

public static class AppUpdates
{
    private static DispatcherQueueTimer? timer;
    private static UpdateManager? manager;
    private static Task<UpdateCheck>? running;

    public static void Start(MainWindow window)
    {
        timer = window.DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromHours(24);
        timer.IsRepeating = true;
        timer.Tick += (_, _) => CheckAutomatically();
        timer.Start();
        CheckAutomatically();
    }

    private static void CheckAutomatically()
    {
        if (Services.Settings.CheckForUpdates)
        {
            _ = CheckAsync();
        }
    }

    public static Task<UpdateCheck> CheckAsync() =>
        running is { IsCompleted: false } inFlight ? inFlight : running = RunAsync();

    public static async Task CheckNowAsync()
    {
        switch (await CheckAsync())
        {
            case UpdateCheck.UpToDate:
                Toasts.Show("You're up to date", $"Tsuzuki {manager?.CurrentVersion} is the newest Windows build.", Icons.CircleCheck);
                break;
            case UpdateCheck.NotInstalled:
                Toasts.Show("Updates need the installed app", "Install Tsuzuki with its setup file to get updates.", Icons.Download);
                break;
            case UpdateCheck.Failed:
                Toasts.Show("Could not check for updates", "GitHub could not be reached. Try again later.", Icons.Download);
                break;
        }
    }

    private static async Task<UpdateCheck> RunAsync()
    {
        if (DevHarness.Active)
        {
            return UpdateCheck.NotInstalled;
        }
        try
        {
            manager ??= new UpdateManager(new WindowsUpdateSource());
            if (!manager.IsInstalled)
            {
                return UpdateCheck.NotInstalled;
            }
            if (manager.UpdatePendingRestart is { } pending)
            {
                Offer(pending.Version);
                return UpdateCheck.Ready;
            }
            var update = await manager.CheckForUpdatesAsync();
            if (update is null)
            {
                return UpdateCheck.UpToDate;
            }
            await manager.DownloadUpdatesAsync(update);
            Offer(update.TargetFullRelease.Version);
            return UpdateCheck.Ready;
        }
        catch (Exception e)
        {
            Log.Error("updates", e);
            return UpdateCheck.Failed;
        }
    }

    private static void Offer(SemanticVersion version)
    {
        Toasts.Show("Update ready", $"Tsuzuki {version} is downloaded. Restart to apply it.", Icons.Download,
            "Restart", () => _ = RestartAsync(), seconds: 30);
    }

    private static async Task RestartAsync()
    {
        if (manager is not { IsInstalled: true } updates)
        {
            return;
        }
        var asset = updates.UpdatePendingRestart;
        if (asset is null)
        {
            return;
        }
        await AppHost.PrepareExitAsync();
        updates.ApplyUpdatesAndRestart(asset);
    }
}
