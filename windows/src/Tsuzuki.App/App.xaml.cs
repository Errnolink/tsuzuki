using Microsoft.UI.Xaml;

namespace Tsuzuki.App;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        if (!DevHarness.Active && Tsuzuki.Core.AppIdentity.IsPreview)
        {
            try
            {
                Tsuzuki.Core.Storage.LibrarySnapshot.SeedFrom(Tsuzuki.Core.AppPaths.Stable, Tsuzuki.Core.AppPaths.Default);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or Microsoft.Data.Sqlite.SqliteException)
            {
                Log.Error("preview-seed", e);
            }
        }
        Services.Settings = AppSettings.Load(DevHarness.ResolvePaths().Settings);
        DevHarness.ApplyThemeOverride();
        if (Services.Settings.Theme != ThemeChoice.System)
        {
            RequestedTheme = Services.Settings.Theme == ThemeChoice.Light ? ApplicationTheme.Light : ApplicationTheme.Dark;
        }
        AppDomain.CurrentDomain.UnhandledException += (_, e) =>
        {
            if (e.ExceptionObject is Exception fatal)
            {
                Log.Error("fatal", fatal);
            }
            Log.Trail("fatal " + (e.ExceptionObject as Exception)?.GetType().Name);
        };
        UnhandledException += (_, e) =>
        {
            Log.Error("unhandled", e.Exception);
            Log.Trail("unhandled " + e.Exception.GetType().Name);
            e.Handled = true;
        };
        TaskScheduler.UnobservedTaskException += (_, e) =>
        {
            Log.Error("unobserved", e.Exception);
            e.SetObserved();
        };
    }

    public static MainWindow MainWindow { get; private set; } = null!;


    protected override async void OnLaunched(LaunchActivatedEventArgs args)
    {
        MainWindow = new MainWindow();
        Program.PublishWindowHandle(WinRT.Interop.WindowNative.GetWindowHandle(MainWindow));
        if (!Environment.GetCommandLineArgs().Contains("--background", StringComparer.OrdinalIgnoreCase))
        {
            MainWindow.Activate();
        }
        await Log.Guard("startup", () => AppHost.StartAsync(MainWindow));
    }
}
