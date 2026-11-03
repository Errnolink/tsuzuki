using Microsoft.UI.Xaml;

namespace Tsuzuki.App;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        UnhandledException += (_, e) =>
        {
            Log.Error("unhandled", e.Exception);
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
