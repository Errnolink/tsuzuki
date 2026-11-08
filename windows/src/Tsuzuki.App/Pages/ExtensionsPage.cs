using System.Globalization;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Automation.Peers;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media.Animation;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Extensions;

namespace Tsuzuki.App.Pages;

public sealed partial class ExtensionsPage : TzPage, ITzPage
{
    private static readonly DataTemplate RowTemplate = (DataTemplate)XamlReader.Load(
        """<DataTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Grid MinHeight="64" /></DataTemplate>""");

    private readonly ListView list;
    private readonly StackPanel storesPanel = new() { Spacing = 4 };
    private readonly StackPanel syncedPanel = new() { Spacing = 4 };
    private readonly TextBox storeInput = new() { PlaceholderText = "Paste an extension store link", MinWidth = 360 };
    private readonly Button addStore = new() { Content = "Add store", MinWidth = 96 };
    private readonly TextBlock storeError = new() { TextWrapping = TextWrapping.Wrap, Visibility = Visibility.Collapsed };
    private readonly TextBlock runtimeNote = new() { TextWrapping = TextWrapping.Wrap };
    private readonly TextBox search = new() { PlaceholderText = "Search extensions", Width = 260 };
    private readonly ComboBox language = new() { MinWidth = 170 };
    private readonly ComboBox show = new() { ItemsSource = new[] { "All", "Installed", "Updates" }, SelectedIndex = 0, MinWidth = 130 };
    private readonly TextBlock status = new() { TextWrapping = TextWrapping.Wrap };
    private readonly ProgressRing loading = new() { IsActive = false, Width = 20, Height = 20 };
    private readonly StackPanel filters = new() { Orientation = Orientation.Horizontal, Spacing = 8 };
    private readonly DispatcherTimer searchTimer = new();
    private readonly Dictionary<string, string> busy = [];
    private readonly Dictionary<string, Row> bound = [];
    private readonly StackPanel titleBlock = new();
    private IReadOnlyList<CatalogEntry> catalog = [];
    private bool loaded;
    private int version;

    public ExtensionsPage()
    {
        list = new ListView
        {
            SelectionMode = ListViewSelectionMode.None,
            ItemTemplate = RowTemplate,
            ItemContainerTransitions = new TransitionCollection(),
            Padding = new Thickness(36, 28, 36, 56),
            Header = BuildHeader(),
        };
        var itemStyle = new Style(typeof(ListViewItem));
        itemStyle.Setters.Add(new Setter(ListViewItem.HorizontalContentAlignmentProperty, HorizontalAlignment.Stretch));
        itemStyle.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(0)));
        itemStyle.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(0, 0, 0, 4)));
        itemStyle.Setters.Add(new Setter(Control.IsTabStopProperty, false));
        list.ItemContainerStyle = itemStyle;
        list.ContainerContentChanging += OnContainerChanging;
        AutomationProperties.SetName(list, "Extensions");
        Content = list;

        addStore.Style = Res<Style>("AccentButtonStyle");
        addStore.Click += (_, _) => _ = Log.Guard("extensions-add-store", AddStoreAsync);
        storeInput.KeyDown += (_, e) =>
        {
            if (e.Key == Windows.System.VirtualKey.Enter)
            {
                e.Handled = true;
                _ = Log.Guard("extensions-add-store", AddStoreAsync);
            }
        };
        searchTimer.Interval = TimeSpan.FromMilliseconds(250);
        searchTimer.Tick += (_, _) =>
        {
            searchTimer.Stop();
            Show();
        };
        search.TextChanged += (_, _) =>
        {
            searchTimer.Stop();
            searchTimer.Start();
        };
        language.SelectionChanged += (_, _) => Show();
        show.SelectionChanged += (_, _) => Show();
        storeError.Foreground = Theme.Brush("TzDanger");
        Unloaded += (_, _) => Detach();
        Loaded += (_, _) => Attach();
    }

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    private UIElement BuildHeader()
    {
        var header = new StackPanel { Spacing = 24, Margin = new Thickness(0, 0, 0, 16), MaxWidth = 1080, HorizontalAlignment = HorizontalAlignment.Stretch };
        titleBlock.Children.Add(new TextBlock { Text = "Extensions", Style = Res<Style>("TzLargeTitle"), Margin = new Thickness(0, 2, 0, 0) });
        titleBlock.Children.Add(new TextBlock
        {
            Text = "Install Mihon extensions to read more sources on this PC. Tsuzuki's own MangaDex and Weeb Central sources always take precedence.",
            Style = Res<Style>("TzSecondary"),
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 6, 0, 0),
        });
        header.Children.Add(titleBlock);

        var add = new Grid { ColumnSpacing = 8 };
        add.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        add.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        add.Children.Add(storeInput);
        Grid.SetColumn(addStore, 1);
        add.Children.Add(addStore);
        AutomationProperties.SetName(storeInput, "Extension store link");
        var stores = new StackPanel { Spacing = 8 };
        stores.Children.Add(new TextBlock { Text = "Stores", Style = Res<Style>("TzBodyStrong") });
        stores.Children.Add(storesPanel);
        stores.Children.Add(add);
        stores.Children.Add(storeError);
        stores.Children.Add(syncedPanel);
        runtimeNote.Style = Res<Style>("TzCaption");
        stores.Children.Add(runtimeNote);
        header.Children.Add(stores);

        AutomationProperties.SetName(search, "Search extensions");
        AutomationProperties.SetName(language, "Language");
        AutomationProperties.SetName(show, "Show");
        var refresh = new Button { Content = "Refresh" };
        refresh.Click += (_, _) => _ = Log.Guard("extensions-refresh", () => LoadAsync(refresh: true));
        filters.Children.Add(search);
        filters.Children.Add(language);
        filters.Children.Add(show);
        filters.Children.Add(refresh);
        filters.Children.Add(loading);
        var catalogHeader = new StackPanel { Spacing = 8 };
        catalogHeader.Children.Add(filters);
        status.Style = Res<Style>("TzCaption");
        AutomationProperties.SetLiveSetting(status, AutomationLiveSetting.Polite);
        catalogHeader.Children.Add(status);
        header.Children.Add(catalogHeader);
        return header;
    }

    public void OnShown()
    {
        if (!loaded)
        {
            loaded = true;
            _ = Log.Guard("extensions-load", () => LoadAsync(refresh: false));
        }
    }

    private bool attached;

    private void Attach()
    {
        if (attached || Services.Engine is not { } engine)
        {
            return;
        }
        attached = true;
        engine.Extensions.Changed += OnExtensionsChanged;
        if (!loaded)
        {
            loaded = true;
            _ = Log.Guard("extensions-load", () => LoadAsync(refresh: false));
        }
    }

    private void Detach()
    {
        if (attached && Services.Engine is { } engine)
        {
            engine.Extensions.Changed -= OnExtensionsChanged;
        }
        attached = false;
    }

    private void OnExtensionsChanged(object? sender, EventArgs e) =>
        Ui.Enqueue(DispatcherQueue, () => _ = Log.Guard("extensions-changed", () => LoadAsync(refresh: false)), "extensions-changed");

    private async Task LoadAsync(bool refresh)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var my = ++version;
        BuildStores(engine.Extensions);
        loading.IsActive = true;
        try
        {
            var synced = await engine.Extensions.SyncedStoresAsync();
            if (my == version)
            {
                BuildSynced(synced);
            }
            if (engine.Extensions.Stores.Count == 0)
            {
                catalog = [];
                status.Text = synced.Count > 0
                    ? "Add a store to see its extensions. Your phone's stores are listed above."
                    : "Add a store to see its extensions. Paste its link above.";
                Show();
                return;
            }
            status.Text = "Loading extensions…";
            var entries = await engine.Extensions.CatalogAsync(refresh);
            if (my != version)
            {
                return;
            }
            catalog = entries;
            BuildLanguages();
            Show();
        }
        catch (Exception e) when (e is HttpRequestException or InvalidDataException or TaskCanceledException)
        {
            if (my == version)
            {
                status.Text = "Couldn't load extensions: " + e.Message;
            }
        }
        finally
        {
            if (my == version)
            {
                loading.IsActive = false;
            }
        }
    }

    private void BuildStores(ExtensionManager manager)
    {
        storesPanel.Children.Clear();
        foreach (var store in manager.Stores)
        {
            var row = new Grid { ColumnSpacing = 12, Padding = new Thickness(16, 10, 12, 10), CornerRadius = new CornerRadius(6), Background = Theme.Brush("TzCardBackground") };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var text = new StackPanel();
            text.Children.Add(new TextBlock { Text = store.Name, Style = Res<Style>("TzBodyStrong") });
            text.Children.Add(new TextBlock { Text = store.IndexUrl, Style = Res<Style>("TzCaption"), IsTextSelectionEnabled = true, TextTrimming = TextTrimming.CharacterEllipsis });
            row.Children.Add(text);
            var remove = new Button { Content = "Remove" };
            AutomationProperties.SetName(remove, $"Remove {store.Name}");
            remove.Click += (_, _) => manager.RemoveStore(store.IndexUrl);
            Grid.SetColumn(remove, 1);
            row.Children.Add(remove);
            storesPanel.Children.Add(row);
        }
        runtimeNote.Text = manager.Runtime.Installed
            ? string.Empty
            : $"The first install also downloads extension support once (about {ExtensionRuntime.DownloadBytes / 1_000_000} MB: a Java runtime and Suwayomi's Android compatibility layer).";
        runtimeNote.Visibility = manager.Runtime.Installed ? Visibility.Collapsed : Visibility.Visible;
    }

    private void BuildSynced(IReadOnlyList<SyncedStore> synced)
    {
        syncedPanel.Children.Clear();
        foreach (var store in synced)
        {
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            row.Children.Add(new TextBlock { Text = $"From your phone: {store.Name}", VerticalAlignment = VerticalAlignment.Center, Style = Res<Style>("TzSecondary") });
            var add = new Button { Content = "Add" };
            AutomationProperties.SetName(add, $"Add {store.Name} from your phone");
            add.Click += (_, _) =>
            {
                storeInput.Text = store.IndexUrl;
                _ = Log.Guard("extensions-add-synced", AddStoreAsync);
            };
            row.Children.Add(add);
            syncedPanel.Children.Add(row);
        }
    }

    private async Task AddStoreAsync()
    {
        if (Services.Engine is not { } engine || storeInput.Text.Trim().Length == 0)
        {
            return;
        }
        addStore.IsEnabled = false;
        storeError.Visibility = Visibility.Collapsed;
        try
        {
            var store = await engine.Extensions.AddStoreAsync(storeInput.Text);
            storeInput.Text = string.Empty;
            Toasts.Show($"Added {store.Name}", glyph: Icons.Check);
        }
        catch (Exception e) when (e is HttpRequestException or InvalidDataException or FormatException or TaskCanceledException)
        {
            storeError.Text = e.Message;
            storeError.Visibility = Visibility.Visible;
        }
        finally
        {
            addStore.IsEnabled = true;
        }
    }

    private void BuildLanguages()
    {
        var current = language.SelectedItem as string;
        var codes = catalog.Select(c => c.Extension.Language).Distinct().OrderBy(c => c == "all" ? "" : LanguageName(c)).ToList();
        var labels = new List<string> { "All languages" };
        labels.AddRange(codes.Select(LanguageName));
        language.ItemsSource = labels;
        var preferred = current ?? LanguageName(CultureInfo.CurrentUICulture.TwoLetterISOLanguageName);
        language.SelectedIndex = Math.Max(0, labels.IndexOf(preferred));
    }

    private static string LanguageName(string code)
    {
        if (code is "all" or "")
        {
            return "Multi";
        }
        try
        {
            return CultureInfo.GetCultureInfo(code).EnglishName;
        }
        catch (CultureNotFoundException)
        {
            return code;
        }
    }

    private void Show()
    {
        var query = search.Text.Trim();
        var lang = language.SelectedIndex > 0 ? language.SelectedItem as string : null;
        var visible = catalog
            .Where(c => query.Length == 0 || c.Extension.Name.Contains(query, StringComparison.CurrentCultureIgnoreCase))
            .Where(c => lang is null || c.Extension.Language == "all" || LanguageName(c.Extension.Language) == lang)
            .Where(c => show.SelectedIndex switch { 1 => c.Installed is not null, 2 => c.HasUpdate, _ => true })
            .OrderByDescending(c => c.HasUpdate)
            .ThenByDescending(c => c.Installed is not null)
            .ThenBy(c => c.Extension.Name, StringComparer.CurrentCultureIgnoreCase)
            .ToList();
        var installedMissing = Services.Engine?.Extensions.Installed
            .Where(i => catalog.All(c => c.Extension.Package != i.Package))
            .Where(i => query.Length == 0 || i.Name.Contains(query, StringComparison.CurrentCultureIgnoreCase))
            .Select(i => new CatalogEntry(new ExtensionStore(i.StoreUrl, string.Empty, null, null),
                new StoreExtension(i.Name, i.Package, string.Empty, i.IconUrl, "1.6", i.VersionCode, i.VersionName, i.Warning, []), i))
            .ToList() ?? [];
        if (show.SelectedIndex != 2)
        {
            visible.InsertRange(0, installedMissing);
        }
        filters.Visibility = catalog.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        bound.Clear();
        list.ItemsSource = visible;
        if (catalog.Count > 0)
        {
            var installed = catalog.Count(c => c.Installed is not null);
            var updates = catalog.Count(c => c.HasUpdate);
            status.Text = $"{visible.Count} of {catalog.Count} extensions · {installed} installed" + (updates > 0 ? $" · {updates} with updates" : string.Empty);
        }
    }

    private void OnContainerChanging(ListViewBase sender, ContainerContentChangingEventArgs args)
    {
        if (args.InRecycleQueue || args.Item is not CatalogEntry entry || args.ItemContainer.ContentTemplateRoot is not Grid root)
        {
            return;
        }
        if (root.Tag is not Row row)
        {
            row = new Row(root, this);
            root.Tag = row;
        }
        foreach (var stale in bound.Where(b => b.Value == row).Select(b => b.Key).ToList())
        {
            bound.Remove(stale);
        }
        bound[entry.Extension.Package] = row;
        row.Bind(entry, busy.GetValueOrDefault(entry.Extension.Package));
        AutomationProperties.SetName(args.ItemContainer, entry.Extension.Name);
        args.Handled = true;
    }

    private void SetBusy(CatalogEntry entry, string? text)
    {
        if (text is null)
        {
            busy.Remove(entry.Extension.Package);
        }
        else
        {
            busy[entry.Extension.Package] = text;
        }
        if (bound.TryGetValue(entry.Extension.Package, out var row) && row.Entry?.Extension.Package == entry.Extension.Package)
        {
            row.Bind(row.Entry, text);
        }
    }

    private async Task RunAsync(CatalogEntry entry)
    {
        if (Services.Engine is not { } engine || busy.ContainsKey(entry.Extension.Package))
        {
            return;
        }
        if (entry.Installed is not null && !entry.HasUpdate)
        {
            engine.Extensions.Uninstall(entry.Extension.Package);
            Toasts.Show($"Removed {entry.Extension.Name}", glyph: Icons.Check);
            return;
        }
        SetBusy(entry, "Starting…");
        var progress = new Progress<RuntimeProgress>(p => SetBusy(entry,
            p.Total > 0 ? $"{p.Step} · {p.Done * 100 / p.Total}%" : p.Step + "…"));
        try
        {
            await engine.Extensions.InstallAsync(entry.Store, entry.Extension, progress);
            SetBusy(entry, null);
            Toasts.Show($"Installed {entry.Extension.Name}", "Find it in Browse.", Icons.Check);
        }
        catch (Exception e) when (e is HttpRequestException or InvalidDataException or NotSupportedException or ExtensionHostException or IOException or TaskCanceledException)
        {
            SetBusy(entry, null);
            Toasts.Show($"Couldn't install {entry.Extension.Name}", e.Message, Icons.Info);
        }
    }

    private sealed class Row
    {
        private readonly TextBlock name = new() { TextTrimming = TextTrimming.CharacterEllipsis };
        private readonly TextBlock detail = new() { TextTrimming = TextTrimming.CharacterEllipsis };
        private readonly Border warning = new() { CornerRadius = new CornerRadius(4), Padding = new Thickness(6, 1, 6, 2), VerticalAlignment = VerticalAlignment.Center };
        private readonly TextBlock warningText = new() { FontSize = 11 };
        private readonly Button action = new() { MinWidth = 104 };

        public Row(Grid root, ExtensionsPage page)
        {
            root.ColumnSpacing = 12;
            root.Padding = new Thickness(16, 10, 12, 10);
            root.CornerRadius = new CornerRadius(6);
            root.Background = Theme.Brush("TzCardBackground");
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            root.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            name.Style = Res<Style>("TzBodyStrong");
            detail.Style = Res<Style>("TzCaption");
            var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            text.Children.Add(name);
            text.Children.Add(detail);
            root.Children.Add(text);
            warning.Child = warningText;
            warning.Background = Theme.Brush("TzChipBackground");
            warningText.Foreground = Theme.Brush("TzDanger");
            Grid.SetColumn(warning, 1);
            root.Children.Add(warning);
            action.VerticalAlignment = VerticalAlignment.Center;
            action.Click += (_, _) =>
            {
                if (Entry is { } entry)
                {
                    _ = Log.Guard("extensions-action", () => page.RunAsync(entry));
                }
            };
            Grid.SetColumn(action, 2);
            root.Children.Add(action);
        }

        public CatalogEntry? Entry { get; private set; }

        public void Bind(CatalogEntry entry, string? busyText)
        {
            Entry = entry;
            var extension = entry.Extension;
            name.Text = extension.Name;
            var parts = new List<string>();
            if (entry.Installed is { } installed)
            {
                parts.Add(entry.HasUpdate ? $"v{installed.VersionName} → v{extension.VersionName}" : $"v{installed.VersionName} installed");
            }
            else
            {
                parts.Add("v" + extension.VersionName);
            }
            parts.Add(LanguageName(extension.Language));
            if (extension.Sources.Count > 1)
            {
                parts.Add($"{extension.Sources.Count} sources");
            }
            var supported = entry.Installed is not null || ExtensionManager.IsSupportedLib(extension.Lib);
            if (!supported)
            {
                parts.Add($"needs extension library {extension.Lib}");
            }
            detail.Text = busyText ?? string.Join(" · ", parts);
            warning.Visibility = extension.Warning is ContentWarning.Nsfw or ContentWarning.Mixed ? Visibility.Visible : Visibility.Collapsed;
            warningText.Text = extension.Warning == ContentWarning.Mixed ? "Mixed 18+" : "18+";
            action.Content = busyText is not null ? "Working…" : entry.HasUpdate ? "Update" : entry.Installed is not null ? "Uninstall" : "Install";
            action.Style = entry.Installed is null || entry.HasUpdate ? Res<Style>("AccentButtonStyle") : null;
            action.IsEnabled = busyText is null && supported;
            AutomationProperties.SetName(action, $"{action.Content} {extension.Name}");
        }
    }
}
