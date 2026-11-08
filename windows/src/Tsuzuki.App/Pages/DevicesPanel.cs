using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using QRCoder;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Sync;
using Windows.ApplicationModel.DataTransfer;

namespace Tsuzuki.App.Pages;

public static class DevicesPanel
{
    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public static void Show() => _ = ShowAsync();

    private static async Task ShowAsync()
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var root = new StackPanel { Spacing = 0, Width = 640 };
        root.Children.Add(new TextBlock { Text = "SYNC · 同期", Style = Res<Style>("TzKicker") });
        root.Children.Add(new TextBlock { Text = "Pair your phone", Style = Res<Style>("TzLargeTitle"), Margin = new Thickness(0, 4, 0, 0) });
        root.Children.Add(new TextBlock
        {
            Text = "Your phone syncs with this PC over Wi-Fi: library, categories and the exact page you're on. Keep both on the same network.",
            Style = Res<Style>("TzSecondary"),
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 8, 0, 22),
        });

        var body = new Grid { ColumnSpacing = 28 };
        body.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var qrImage = new Image { Width = 196, Height = 196 };
        var qrFrame = new Border
        {
            Background = new SolidColorBrush(Microsoft.UI.Colors.White),
            CornerRadius = Res<CornerRadius>("TzCardRadius"),
            Padding = new Thickness(12),
            Child = qrImage,
            VerticalAlignment = VerticalAlignment.Top,
        };
        body.Children.Add(qrFrame);

        var steps = new StackPanel { Spacing = 14 };
        Grid.SetColumn(steps, 1);
        body.Children.Add(steps);
        root.Children.Add(body);
        var warningText = new TextBlock { Style = Res<Style>("TzBody"), TextWrapping = TextWrapping.Wrap };
        var openSettings = new HyperlinkButton { Content = "Open network settings", Padding = new Thickness(0), Margin = new Thickness(0, 6, 0, 0) };
        openSettings.Click += (_, _) => _ = Log.Guard("devices-settings", async () => await Windows.System.Launcher.LaunchUriAsync(new Uri("ms-settings:network-status")));
        var warningBody = new StackPanel();
        warningBody.Children.Add(warningText);
        warningBody.Children.Add(openSettings);
        var warning = new Border
        {
            Visibility = Visibility.Collapsed,
            Margin = new Thickness(0, 20, 0, 0),
            Padding = new Thickness(16, 12, 16, 12),
            CornerRadius = Res<CornerRadius>("TzCardRadius"),
            Background = Design.Theme.Brush("TzCard"),
            BorderBrush = Design.Theme.Brush("TzWarning"),
            BorderThickness = new Thickness(1),
            Child = warningBody,
        };
        root.Children.Add(warning);
        var primaryHost = new Uri(engine.Hub.GetPairing().PrimaryUrl).Host;
        _ = Task.Run(() => NetworkCheck.PublicNetworkNameAsync(primaryHost)).ContinueWith(t =>
        {
            if (t.IsCompletedSuccessfully && t.Result is { } network)
            {
                Ui.Enqueue(root.DispatcherQueue, () =>
                {
                    warningText.Text = $"Windows treats “{network}” as a public network and blocks your phone from reaching this PC. Set it to Private in network settings, then pair.";
                    warning.Visibility = Visibility.Visible;
                }, "devices-network");
            }
            else if (t.Exception is { } error)
            {
                Log.Error("devices-network", error);
            }
        }, TaskScheduler.Default);

        var devicesHeader = new TextBlock { Text = "PAIRED DEVICES", Style = Res<Style>("TzKicker"), Margin = new Thickness(0, 28, 0, 8) };
        var devices = new StackPanel { Spacing = 6 };
        root.Children.Add(devicesHeader);
        root.Children.Add(devices);

        var status = new TextBlock { Style = Res<Style>("TzCaption"), VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap };
        var footer = new Grid { Margin = new Thickness(0, 26, 0, 0), ColumnSpacing = 10 };
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footer.Children.Add(status);
        var newKey = new Button { Style = Res<Style>("TzGhostButton"), Content = "New key" };
        ToolTipService.SetToolTip(newKey, "Paired phones will need the new key");
        Grid.SetColumn(newKey, 1);
        footer.Children.Add(newKey);
        var done = new Button { Style = Res<Style>("TzAccentButton"), Content = "Done", Padding = new Thickness(22, 0, 22, 0) };
        done.Click += (_, _) => _ = App.MainWindow.CloseSheetAsync();
        Grid.SetColumn(done, 2);
        footer.Children.Add(done);
        root.Children.Add(footer);

        async Task Render(PairingInfo pairing)
        {
            qrImage.Source = await QrAsync(pairing.DeepLink);
            steps.Children.Clear();
            steps.Children.Add(Step("1", "On your phone, open Tsuzuki › Settings › Data › Sync and choose SyncYomi."));
            steps.Children.Add(Step("2", "Scan this code with your phone's camera, or enter these:"));
            steps.Children.Add(Field("Server address", pairing.PrimaryUrl));
            steps.Children.Add(Field("Key", pairing.ApiKey));
            steps.Children.Add(Step("3", "Tap Sync now. Your library appears here within seconds."));
            var hub = engine.Hub.Status;
            status.Text = hub.Running
                ? $"Listening on {string.Join(", ", hub.Addresses)} · port {hub.Port}"
                : "Sync is off: " + (hub.Error ?? "the hub isn't running.");
            status.Foreground = Design.Theme.Brush(hub.Running ? "TzTextSecondary" : "TzDanger");
        }

        async Task RenderDevices()
        {
            devices.Children.Clear();
            var list = await engine.Hub.GetDevicesAsync();
            if (list.Count == 0)
            {
                devices.Children.Add(new TextBlock { Text = "No phone has synced yet.", Style = Res<Style>("TzSecondary") });
                return;
            }
            foreach (var device in list.OrderByDescending(d => d.LastSeenAt))
            {
                devices.Children.Add(DeviceRow(device, engine, RenderDevices));
            }
        }

        newKey.Click += (_, _) => _ = Log.Guard("devices-key", async () =>
        {
            if (await Dialogs.ConfirmAsync("Make a new key?", "Phones paired with the old key stop syncing until you enter the new one.", "Make new key"))
            {
                await Render(await engine.Hub.RegenerateKeyAsync());
            }
        });

        await Render(engine.Hub.GetPairing());
        await RenderDevices();
        void OnSynced(object? s, DeviceSyncedEventArgs e) => Ui.Enqueue(root.DispatcherQueue, RenderDevices, "devices-render");
        engine.Hub.DeviceSynced += OnSynced;
        void OnClosed(object? s, EventArgs e)
        {
            engine.Hub.DeviceSynced -= OnSynced;
            App.MainWindow.SheetClosed -= OnClosed;
        }
        App.MainWindow.SheetClosed += OnClosed;
        App.MainWindow.ShowSheet(root);
    }

    private static FrameworkElement Step(string number, string text)
    {
        var grid = new Grid { ColumnSpacing = 12 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.Children.Add(new Border
        {
            Width = 24,
            Height = 24,
            CornerRadius = new CornerRadius(12),
            Background = Design.Theme.Brush("TzAccentSoft"),
            VerticalAlignment = VerticalAlignment.Top,
            Child = new TextBlock { Text = number, FontSize = 12, FontWeight = FontWeights.Bold, Foreground = Design.Theme.Brush("TzAccent"), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center },
        });
        var label = new TextBlock { Text = text, Style = Res<Style>("TzBody"), TextWrapping = TextWrapping.Wrap };
        Grid.SetColumn(label, 1);
        grid.Children.Add(label);
        return grid;
    }

    private static FrameworkElement Field(string label, string value)
    {
        var grid = new Grid { Margin = new Thickness(36, 0, 0, 0), Padding = new Thickness(14, 10, 8, 10), CornerRadius = Res<CornerRadius>("TzCardRadius"), Background = Design.Theme.Brush("TzCard"), BorderBrush = Design.Theme.Brush("TzStroke"), BorderThickness = new Thickness(1) };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var text = new StackPanel();
        text.Children.Add(new TextBlock { Text = label.ToUpperInvariant(), Style = Res<Style>("TzKicker"), FontSize = 10 });
        text.Children.Add(new TextBlock { Text = value, FontFamily = new FontFamily("Cascadia Mono, Consolas"), FontSize = 16, FontWeight = FontWeights.SemiBold, IsTextSelectionEnabled = true, Margin = new Thickness(0, 3, 0, 0) });
        grid.Children.Add(text);
        var copy = new Button { Style = Res<Style>("TzIconButton"), Content = new FontIcon { Glyph = Icons.Copy, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 15 } };
        ToolTipService.SetToolTip(copy, "Copy");
        copy.Click += (_, _) =>
        {
            var package = new DataPackage();
            package.SetText(value);
            Clipboard.SetContent(package);
            Toasts.Show("Copied", label, Icons.Copy, seconds: 2);
        };
        Grid.SetColumn(copy, 1);
        grid.Children.Add(copy);
        return grid;
    }

    private static FrameworkElement DeviceRow(SyncDevice device, Tsuzuki.Core.Engine engine, Func<Task> onRemoved)
    {
        var grid = new Grid { ColumnSpacing = 12, Padding = new Thickness(12, 10, 12, 10), CornerRadius = Res<CornerRadius>("TzCardRadius"), Background = Design.Theme.Brush("TzCard") };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(new FontIcon { Glyph = Icons.Smartphone, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 18, Foreground = Design.Theme.Brush("TzAccent") });
        var text = new StackPanel();
        text.Children.Add(new TextBlock { Text = device.Name, Style = Res<Style>("TzBodyStrong") });
        text.Children.Add(new TextBlock { Text = "Last synced " + Format.Relative(device.LastSeenAt), Style = Res<Style>("TzCaption") });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);

        var remove = new Button { Style = Res<Style>("TzIconButton"), Content = new FontIcon { Glyph = Icons.Close, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 15 }, Foreground = Design.Theme.Brush("TzDanger") };
        ToolTipService.SetToolTip(remove, "Remove device");
        remove.Click += (_, _) => _ = Log.Guard("device-remove", async () =>
        {
            if (await Dialogs.ConfirmAsync($"Remove {device.Name}?", "This phone will be forgotten. Because Tsuzuki uses a shared sync key, make a new key if you want to stop this device connecting.", "Remove"))
            {
                await engine.Hub.RemoveDeviceAsync(device.DeviceId);
                await onRemoved();
                Toasts.Show("Device removed", $"{device.Name} was removed from the list.", Icons.Check, seconds: 3);
            }
        });
        Grid.SetColumn(remove, 2);
        grid.Children.Add(remove);
        return grid;
    }

    public static async Task<BitmapImage> QrAsync(string text)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(text, QRCodeGenerator.ECCLevel.M);
        var png = new PngByteQRCode(data).GetGraphic(12, [16, 17, 20, 255], [255, 255, 255, 255], drawQuietZones: false);
        var bitmap = new BitmapImage();
        using var stream = new MemoryStream(png);
        await bitmap.SetSourceAsync(stream.AsRandomAccessStream());
        return bitmap;
    }
}
