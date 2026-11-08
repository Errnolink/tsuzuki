using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppLifecycle;
using Velopack;

namespace Tsuzuki.App;

public static partial class Program
{
    [LibraryImport("kernel32.dll", EntryPoint = "CreateEventW", StringMarshalling = StringMarshalling.Utf16)]
    private static partial IntPtr CreateEvent(IntPtr attributes, [MarshalAs(UnmanagedType.Bool)] bool manualReset, [MarshalAs(UnmanagedType.Bool)] bool initialState, string? name);

    [LibraryImport("kernel32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetEvent(IntPtr handle);

    [LibraryImport("ole32.dll")]
    private static partial uint CoWaitForMultipleObjects(uint flags, uint timeout, uint count, [In] IntPtr[] handles, out uint index);

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool ShowWindow(IntPtr hwnd, int command);

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool IsIconic(IntPtr hwnd);
    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetForegroundWindow(IntPtr hwnd);

    [LibraryImport("user32.dll")]
    private static partial uint GetWindowThreadProcessId(IntPtr hwnd, out uint processId);

    private static AppInstance? instance;

    [STAThread]
    private static void Main(string[] args)
    {
        DevHarness.Parse(args);
        VelopackApp.Build().Run();
        WinRT.ComWrappersSupport.InitializeComWrappers();
        if (RedirectedToRunningInstance())
        {
            return;
        }
        Application.Start(parameters =>
        {
            SynchronizationContext.SetSynchronizationContext(new NonPumpingSyncContext(DispatcherQueue.GetForCurrentThread()));
            _ = new App();
        });
    }

    private static bool RedirectedToRunningInstance()
    {
        var key = InstanceKey();
        var main = AppInstance.FindOrRegisterForKey(key);
        if (main.IsCurrent)
        {
            instance = main;
            main.Activated += (_, _) => App.MainWindow?.DispatcherQueue.TryEnqueue(() => App.MainWindow.BringToFront());
            return false;
        }
        var handle = CreateEvent(IntPtr.Zero, true, false, null);
        var activation = AppInstance.GetCurrent().GetActivatedEventArgs();
        Task.Run(() =>
        {
            try
            {
                main.RedirectActivationToAsync(activation).AsTask().Wait(TimeSpan.FromSeconds(10));
            }
            catch (Exception)
            {
            }
            finally
            {
                SetEvent(handle);
            }
        });
        _ = CoWaitForMultipleObjects(0, 12000, 1, [handle], out _);
        var hwnd = PublishedWindowHandle(main.ProcessId);
        if (hwnd == IntPtr.Zero)
        {
            try
            {
                hwnd = Process.GetProcessById((int)main.ProcessId).MainWindowHandle;
            }
            catch (ArgumentException)
            {
            }
        }
        if (hwnd != IntPtr.Zero)
        {
            ShowWindow(hwnd, IsIconic(hwnd) ? 9 : 5);
            SetForegroundWindow(hwnd);
        }
        return true;
    }

    public static void PublishWindowHandle(IntPtr hwnd)
    {
        try
        {
            var path = Path.Combine(DevHarness.ResolvePaths().Root, InstanceKey() + ".hwnd");
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            File.WriteAllText(path, hwnd.ToInt64().ToString());
        }
        catch (IOException)
        {
        }
    }

    private static IntPtr PublishedWindowHandle(uint processId)
    {
        try
        {
            var path = Path.Combine(DevHarness.ResolvePaths().Root, InstanceKey() + ".hwnd");
            if (!long.TryParse(File.ReadAllText(path).Trim(), out var value) || value == 0)
            {
                return IntPtr.Zero;
            }
            var hwnd = new IntPtr(value);
            return IsWindowOfProcess(hwnd, processId) ? hwnd : IntPtr.Zero;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return IntPtr.Zero;
        }
    }

    private static bool IsWindowOfProcess(IntPtr hwnd, uint processId) =>
        GetWindowThreadProcessId(hwnd, out var owner) != 0 && owner == processId;

    private static string InstanceKey() =>
        DevHarness.Current?.Instance is { } name ? $"tsuzuki-{name}" : Tsuzuki.Core.AppIdentity.InstanceKey;
}
