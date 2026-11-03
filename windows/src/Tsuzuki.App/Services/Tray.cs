using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace Tsuzuki.App;

public sealed class Tray : IDisposable
{
    private const uint WmTray = 0x8000 + 17;
    private const uint NimAdd = 0;
    private const uint NimModify = 1;
    private const uint NimDelete = 2;
    private const uint NimSetVersion = 4;
    private const uint NifMessage = 0x1;
    private const uint NifIcon = 0x2;
    private const uint NifTip = 0x4;
    private const uint NifInfo = 0x10;
    private const uint NifShowTip = 0x80;
    private const uint NotifyIconVersion4 = 4;
    private const int WmContextMenu = 0x007B;
    private const int NinSelect = 0x0400;
    private const int NinKeySelect = 0x0401;
    private const uint MfString = 0x0;
    private const uint MfSeparator = 0x800;
    private const uint TpmReturnCmd = 0x100;
    private const uint TpmRightButton = 0x2;
    private const uint ImageIcon = 1;
    private const uint LrLoadFromFile = 0x10;
    private const int CommandOpen = 1;
    private const int CommandQuit = 2;
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string RunValue = "Tsuzuki";

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NotifyIconData
    {
        public uint Size;
        public IntPtr Window;
        public uint Id;
        public uint Flags;
        public uint CallbackMessage;
        public IntPtr Icon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string Tip;
        public uint State;
        public uint StateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
        public string Info;
        public uint Version;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)]
        public string InfoTitle;
        public uint InfoFlags;
        public Guid Item;
        public IntPtr BalloonIcon;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Point
    {
        public int X;
        public int Y;
    }

    private delegate IntPtr SubclassProc(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam, UIntPtr id, UIntPtr data);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern bool Shell_NotifyIconW(uint message, ref NotifyIconData data);

    [DllImport("comctl32.dll")]
    private static extern bool SetWindowSubclass(IntPtr hwnd, SubclassProc proc, UIntPtr id, UIntPtr data);

    [DllImport("comctl32.dll")]
    private static extern bool RemoveWindowSubclass(IntPtr hwnd, SubclassProc proc, UIntPtr id);

    [DllImport("comctl32.dll")]
    private static extern IntPtr DefSubclassProc(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr LoadImageW(IntPtr instance, string name, uint type, int width, int height, uint flags);

    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(IntPtr icon);

    [DllImport("user32.dll")]
    private static extern IntPtr CreatePopupMenu();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool AppendMenuW(IntPtr menu, uint flags, UIntPtr id, string? text);

    [DllImport("user32.dll")]
    private static extern int TrackPopupMenuEx(IntPtr menu, uint flags, int x, int y, IntPtr hwnd, IntPtr parameters);

    [DllImport("user32.dll")]
    private static extern bool DestroyMenu(IntPtr menu);

    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out Point point);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hwnd);

    private readonly IntPtr hwnd;
    private readonly IntPtr icon;
    private readonly SubclassProc proc;
    private bool added;

    public Tray(IntPtr hwnd, string iconPath)
    {
        this.hwnd = hwnd;
        icon = LoadImageW(IntPtr.Zero, iconPath, ImageIcon, 16, 16, LrLoadFromFile);
        proc = WndProc;
        SetWindowSubclass(hwnd, proc, (UIntPtr)17, UIntPtr.Zero);
    }

    public event EventHandler? OpenRequested;
    public event EventHandler? QuitRequested;

    public void Show(string tip)
    {
        var data = Data(tip);
        data.Flags = NifMessage | NifIcon | NifTip | NifShowTip;
        if (!added)
        {
            added = Shell_NotifyIconW(NimAdd, ref data);
            data.Version = NotifyIconVersion4;
            Shell_NotifyIconW(NimSetVersion, ref data);
        }
        else
        {
            Shell_NotifyIconW(NimModify, ref data);
        }
    }

    public void Balloon(string title, string text)
    {
        if (!added)
        {
            return;
        }
        var data = Data(string.Empty);
        data.Flags = NifInfo;
        data.InfoTitle = title;
        data.Info = text;
        Shell_NotifyIconW(NimModify, ref data);
    }

    private NotifyIconData Data(string tip) => new()
    {
        Size = (uint)Marshal.SizeOf<NotifyIconData>(),
        Window = hwnd,
        Id = 1,
        CallbackMessage = WmTray,
        Icon = icon,
        Tip = tip,
        Info = string.Empty,
        InfoTitle = string.Empty,
    };

    private IntPtr WndProc(IntPtr window, uint msg, IntPtr wParam, IntPtr lParam, UIntPtr id, UIntPtr data)
    {
        if (msg == WmTray)
        {
            var evt = (int)(lParam.ToInt64() & 0xFFFF);
            if (evt is NinSelect or NinKeySelect)
            {
                OpenRequested?.Invoke(this, EventArgs.Empty);
            }
            else if (evt == WmContextMenu)
            {
                ShowMenu();
            }
            return IntPtr.Zero;
        }
        return DefSubclassProc(window, msg, wParam, lParam);
    }

    private void ShowMenu()
    {
        var menu = CreatePopupMenu();
        AppendMenuW(menu, MfString, (UIntPtr)CommandOpen, "Open Tsuzuki");
        AppendMenuW(menu, MfSeparator, UIntPtr.Zero, null);
        AppendMenuW(menu, MfString, (UIntPtr)CommandQuit, "Quit (stops phone sync)");
        GetCursorPos(out var point);
        SetForegroundWindow(hwnd);
        var command = TrackPopupMenuEx(menu, TpmReturnCmd | TpmRightButton, point.X, point.Y, hwnd, IntPtr.Zero);
        DestroyMenu(menu);
        if (command == CommandOpen)
        {
            OpenRequested?.Invoke(this, EventArgs.Empty);
        }
        else if (command == CommandQuit)
        {
            QuitRequested?.Invoke(this, EventArgs.Empty);
        }
    }

    public void Dispose()
    {
        if (added)
        {
            var data = Data(string.Empty);
            Shell_NotifyIconW(NimDelete, ref data);
            added = false;
        }
        RemoveWindowSubclass(hwnd, proc, (UIntPtr)17);
        if (icon != IntPtr.Zero)
        {
            DestroyIcon(icon);
        }
    }

    public static bool StartsWithWindows
    {
        get
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKey);
            return key?.GetValue(RunValue) is string;
        }
    }

    public static void SetStartWithWindows(bool enabled)
    {
        using var key = Registry.CurrentUser.CreateSubKey(RunKey);
        if (!enabled)
        {
            key.DeleteValue(RunValue, throwOnMissingValue: false);
            return;
        }
        var exe = Environment.ProcessPath ?? Path.Combine(AppContext.BaseDirectory, "Tsuzuki.exe");
        var current = Path.GetDirectoryName(exe);
        if (current is not null && Path.GetFileName(current).Equals("current", StringComparison.OrdinalIgnoreCase))
        {
            var stub = Path.Combine(Path.GetDirectoryName(current)!, Path.GetFileName(exe));
            if (File.Exists(stub))
            {
                exe = stub;
            }
        }
        key.SetValue(RunValue, $"\"{exe}\" --background");
    }
}
