using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core;
using Tsuzuki.Core.Backup;
using Windows.Storage.Pickers;

namespace Tsuzuki.App.Pages;

public sealed partial class SettingsPage : UserControl, ITzPage
{
    private readonly StackPanel body = new() { Padding = new Thickness(40, 74, 40, 56), MaxWidth = 900, HorizontalAlignment = HorizontalAlignment.Stretch };
    private readonly TextBlock syncStatus = new();
    private readonly TextBlock backupStatus = new();
    private readonly TextBlock backupTarget = new();
    private FrameworkElement? readerModeChips;
    private FrameworkElement? readerDirectionChips;
    private FrameworkElement? driveRow;
    public SettingsPage()
    {
        Content = new ScrollView { Content = body, HorizontalScrollMode = ScrollingScrollMode.Disabled, VerticalScrollBarVisibility = ScrollingScrollBarVisibility.Auto };
        body.Children.Add(new TextBlock { Text = "SETTINGS · 設定", Style = Res<Style>("TzKicker") });
        body.Children.Add(new TextBlock { Text = "Settings", Style = Res<Style>("TzLargeTitle"), Margin = new Thickness(0, 2, 0, 18) });

        syncStatus.Style = Res<Style>("TzCaption");
        var pair = Action("Pair a phone", primary: true, () => DevicesPanel.Show());
        var keep = new ToggleSwitch { IsOn = Services.Settings.KeepSyncing, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        keep.Toggled += (_, _) => { Services.Settings.KeepSyncing = keep.IsOn; Services.Settings.Save(); };
        var startup = new ToggleSwitch { IsOn = Tray.StartsWithWindows, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        startup.Toggled += (_, _) => Tray.SetStartWithWindows(startup.IsOn);
        AddSection("Sync", "同期",
            Row("Phone sync", syncStatus, pair),
            Row("Keep syncing when closed", Caption("Closing the window leaves Tsuzuki in the tray so your phone can still sync."), keep),
            Row("Start with Windows", Caption("Starts quietly in the tray, so your phone can sync whenever this PC is on."), startup));

        backupStatus.Style = Res<Style>("TzCaption");
        backupTarget.Style = Res<Style>("TzCaption");
        var backupNow = Action("Back up now", primary: false, () => _ = BackupNowAsync());
        var changeFolder = Action("Change…", primary: false, () => _ = ChangeBackupFolderAsync());
        var whereActions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Children = { backupNow, changeFolder } };
        var restore = Action("Restore…", primary: false, () => _ = RestoreAsync());
        var auto = new ToggleSwitch { IsOn = Services.Settings.AutoBackup, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        auto.Toggled += (_, _) => { Services.Settings.AutoBackup = auto.IsOn; Services.Settings.Save(); };
        var drive = new ToggleSwitch { IsOn = Services.Settings.BackupDrive, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        drive.Toggled += (_, _) => { Services.Settings.BackupDrive = drive.IsOn; Services.Settings.Save(); _ = RefreshAsync(); };
        driveRow = Row("Google Drive", Caption("Back up into My Drive\\Tsuzuki\\Backups. Off keeps backups on this PC."), drive);
        var keepCount = new NumberBox { Value = Services.Settings.AutoBackupKeep, Minimum = 2, Maximum = 50, MinWidth = 130, SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact };
        keepCount.ValueChanged += (_, e) =>
        {
            if (!double.IsNaN(e.NewValue))
            {
                Services.Settings.AutoBackupKeep = (int)Math.Clamp(e.NewValue, 2, 50);
                Services.Settings.Save();
            }
        };
        AddSection("Backups", "保存",
            Row("Where backups go", backupTarget, whereActions),
            driveRow,
            Row("Daily backup", Caption("Runs once a day while Tsuzuki is open. Your phone can restore them too."), auto),
            Row("Keep backups", Caption("How many recent backups to keep in the backup folder."), keepCount),
            Row("Restore a backup", Caption("Works with backups from this PC, Tsuzuki, Mihon, Komikku and Neko."), restore),
            Row("Last backup", backupStatus, null));

        AddSection("Appearance", "外観",
            Row("Theme", Caption("Follows Windows when set to System."), Chips(["System", "Light", "Dark"], (int)Services.Settings.Theme, i =>
            {
                Services.Settings.Theme = (ThemeChoice)i;
                Services.Settings.Save();
                AppHost.ApplyTheme();
            })),
            Row("Cover size", Caption("Size of covers in the library and browse grids."), CoverSlider()));

        var firstAlone = new ToggleSwitch { IsOn = Services.Settings.FirstPageAlone, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        firstAlone.Toggled += (_, _) => { Services.Settings.FirstPageAlone = firstAlone.IsOn; Services.Settings.Save(); };
        var dataSaver = new ToggleSwitch { IsOn = Services.Settings.DataSaver, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        dataSaver.Toggled += (_, _) => { Services.Settings.DataSaver = dataSaver.IsOn; Services.Settings.Save(); AppHost.ApplySourceOptions(); };
        AddSection("Reader", "読む",
            Row("Default layout", Caption("Long Strip titles open as a strip automatically."), readerModeChips = Chips(["Single", "Two pages", "Strip"], (int)Services.Settings.ReaderMode, i =>
            {
                Services.Settings.ReaderMode = (ReaderMode)i;
                Services.Settings.Save();
            })),
            Row("Reading direction", Caption("Arrow keys and page clicks follow this."), readerDirectionChips = Chips(["Right to left", "Left to right"], Services.Settings.RightToLeft ? 0 : 1, i =>
            {
                Services.Settings.RightToLeft = i == 0;
                Services.Settings.Save();
            })),
            Row("Cover page alone", Caption("In two-page view, show the first page by itself so spreads line up."), firstAlone),
            Row("Data saver", Caption("Load smaller MangaDex images. Faster on slow connections."), dataSaver),
            Row("Shortcuts", Caption("← → or click the edges to turn pages · Space next · 1 2 3 layouts · F fit · R direction · [ ] chapters · F11 full screen · Esc close"), null));

        var checkUpdates = new ToggleSwitch { IsOn = Services.Settings.CheckForUpdates, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        checkUpdates.Toggled += (_, _) => { Services.Settings.CheckForUpdates = checkUpdates.IsOn; Services.Settings.Save(); };
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
        var labMotion = new ToggleSwitch { IsOn = Services.Settings.LabMotion, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        labMotion.Toggled += (_, _) => { Services.Settings.LabMotion = labMotion.IsOn; Services.Settings.Save(); };
        var labLists = new ToggleSwitch { IsOn = Services.Settings.LabLists, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        labLists.Toggled += (_, _) => { Services.Settings.LabLists = labLists.IsOn; Services.Settings.Save(); };
        var labSkeletons = new ToggleSwitch { IsOn = Services.Settings.LabSkeletons, OnContent = string.Empty, OffContent = string.Empty, MinWidth = 0 };
        labSkeletons.Toggled += (_, _) => { Services.Settings.LabSkeletons = labSkeletons.IsOn; Services.Settings.Save(); };
        AddSection("Layout lab", "実験",
            Row("Motion: Fluent timings", Caption("Fluent navigation rhythm: 250 ms drill-in, back always the fastest leg, an entrance rise instead of page zoom, springy chrome and sheets you can interrupt mid-flight."), labMotion),
            Row("Lists: animate changes", Caption("Library, browse and history lists animate items being added, removed and reordered."), labLists),
            Row("Skeletons: pulse", Caption("Loading placeholders breathe with the same travelling shimmer as Android. New placeholders only."), labSkeletons));
        var version = typeof(SettingsPage).Assembly.GetName().Version?.ToString(3) ?? "0.1.0";
        var openFolder = Action("Open data folder", primary: false, () =>
        {
            if (Services.Engine is { } engine)
            {
                _ = Windows.System.Launcher.LaunchFolderPathAsync(engine.Paths.Root);
            }
        });
        AddSection("About", "情報",
            Row("Check for updates", Caption("Looks for new Windows builds on GitHub at startup and once a day. You decide when to restart."), checkUpdates),
            Row("Update now", Caption("Looks for a newer build right away and downloads it."), checkNow),
            Row("Tsuzuki for Windows " + version, Caption("Reads MangaDex natively and keeps your phone in sync. Covers and pages come from MangaDex; Tsuzuki is not affiliated with it."), openFolder),
            Row("Open source", Caption("Icons by Lucide (ISC). Built with the Windows App SDK."), null));
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    private static TextBlock Caption(string text) => new() { Text = text, Style = Res<Style>("TzCaption"), TextWrapping = TextWrapping.Wrap };

    private static Button Action(string label, bool primary, Action onClick)
    {
        var button = new Button { Style = Res<Style>(primary ? "TzAccentButton" : "TzSoftButton"), Content = label, Height = 36, FontSize = 13 };
        button.Click += (_, _) => onClick();
        return button;
    }

    private void AddSection(string title, string japanese, params FrameworkElement[] rows)
    {
        body.Children.Add(new TextBlock { Text = $"{title.ToUpperInvariant()} · {japanese}", Style = Res<Style>("TzKicker"), Margin = new Thickness(4, 22, 0, 10) });
        var card = new StackPanel { Spacing = 0 };
        for (var i = 0; i < rows.Length; i++)
        {
            if (i > 0)
            {
                card.Children.Add(new Border { Height = 1, Background = Design.Theme.Brush("TzStroke"), Margin = new Thickness(18, 0, 18, 0) });
            }
            card.Children.Add(rows[i]);
        }
        body.Children.Add(new Border
        {
            CornerRadius = new CornerRadius(16),
            Background = Design.Theme.Brush("TzCard"),
            BorderBrush = Design.Theme.Brush("TzStroke"),
            BorderThickness = new Thickness(1),
            Child = card,
        });
    }

    private static FrameworkElement Row(string title, FrameworkElement? description, FrameworkElement? control)
    {
        var grid = new Grid { Padding = new Thickness(18, 14, 16, 14), ColumnSpacing = 24, MinHeight = 64 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 3 };
        text.Children.Add(new TextBlock { Text = title, Style = Res<Style>("TzBodyStrong"), TextWrapping = TextWrapping.Wrap });
        if (description is not null)
        {
            text.Children.Add(description);
        }
        grid.Children.Add(text);
        if (control is not null)
        {
            control.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(control, 1);
            grid.Children.Add(control);
        }
        return grid;
    }

    private static FrameworkElement Chips(string[] labels, int selected, Action<int> onSelect)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        for (var i = 0; i < labels.Length; i++)
        {
            var index = i;
            var chip = new ToggleButton { Style = Res<Style>("TzChip"), Content = labels[i], IsChecked = i == selected };
            chip.Click += (_, _) =>
            {
                foreach (var other in panel.Children.OfType<ToggleButton>())
                {
                    other.IsChecked = other == chip;
                }
                onSelect(index);
            };
            panel.Children.Add(chip);
        }
        return panel;
    }

    private static void SelectChip(FrameworkElement? chipsControl, int selectedIndex)
    {
        if (chipsControl is not Panel panel) return;
        var buttons = panel.Children.OfType<ToggleButton>().ToList();
        for (var i = 0; i < buttons.Count; i++)
        {
            buttons[i].IsChecked = i == selectedIndex;
        }
    }

    private static FrameworkElement CoverSlider()
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
        syncStatus.Text = !hub.Running
            ? "Sync is off: " + (hub.Error ?? "the hub isn't running.")
            : devices.Count == 0
                ? "No phone paired yet."
                : string.Join(" · ", devices.OrderByDescending(d => d.LastSeenAt).Select(d => $"{d.Name}, synced {Format.Relative(d.LastSeenAt)}"));
        var target = AppHost.BackupTarget();
        backupTarget.Text = target is null ? "No backup folder available." : $"{target.DisplayName} · {target.Location}";
        backupStatus.Text = Services.Settings.LastAutoBackup == default ? "No backup yet." : Format.Relative(Services.Settings.LastAutoBackup.ToUnixTimeMilliseconds());
        if (driveRow is not null)
        {
            driveRow.Visibility = AppHost.DriveBackupAvailable() ? Visibility.Visible : Visibility.Collapsed;
        }
        SelectChip(readerModeChips, (int)Services.Settings.ReaderMode);
        SelectChip(readerDirectionChips, Services.Settings.RightToLeft ? 0 : 1);
    }

    private async Task BackupNowAsync()
    {
        if (Services.Engine is not { } engine || AppHost.BackupTarget() is not { } target)
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
                $"{file.Name} replaces every title, category and reading progress in your library with the backup's contents.",
                "Restore"))
        {
            return;
        }
        try
        {
            await using var stream = File.OpenRead(file.Path);
            var result = await engine.Backup.RestoreAsync(stream);
            Toasts.Show("Backup restored", $"{result.MangaAdded} new titles · {result.ChaptersUpdated} chapters updated", Icons.CircleCheck);
            await App.MainWindow.NavigateRootAsync("library");
        }
        catch (Exception e) when (e is IOException or InvalidDataException or InvalidOperationException or Google.Protobuf.InvalidProtocolBufferException)
        {
            Toasts.Show("Couldn't restore that file", "It doesn't look like a Tsuzuki or Mihon backup.", Icons.Alert);
        }
    }
}
