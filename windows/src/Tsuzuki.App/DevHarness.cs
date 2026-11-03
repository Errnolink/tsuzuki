using Microsoft.UI.Dispatching;
using Tsuzuki.App.Navigation;
using Tsuzuki.App.Pages;
using Tsuzuki.Core;

namespace Tsuzuki.App;

public sealed class DevHarness
{
    public static DevHarness? Current { get; private set; }

    public string? Instance { get; private init; }
    public AppPaths? Paths { get; private init; }
    public ThemeChoice? Theme { get; private init; }
    public string? Page { get; private init; }
    public long? DetailsId { get; private init; }
    public long? ReaderManga { get; private init; }
    public long? ReaderChapter { get; private init; }
    public int ReaderPage { get; private init; }
    public ReaderMode? Mode { get; private init; }
    public string? Sheet { get; private init; }
    public double? WindowWidth { get; private init; }
    public double? WindowHeight { get; private init; }
    public bool NoHub { get; private init; }
    public string? BrushesOutput { get; private init; }
    public bool CycleThemeLater { get; private init; }
    public double CycleThemeDelay { get; private init; } = 3;

    private static DispatcherQueueTimer? themeTimer;
    public bool DevHover { get; private init; }

    public static bool Active => Current is not null;

    public static bool HoverForced => Current is { DevHover: true };

    public static void Parse(string[] args)
    {
        string? instance = null, data = null, page = null, sheet = null;
        long? details = null, readerManga = null, readerChapter = null;
        var readerPage = 0;
        ReaderMode? readerMode = null;
        ThemeChoice? theme = null;
        double? width = null, height = null;
        var noHub = false;
        var devHover = false;
        string? brushesOutput = null;
        var cycleTheme = false;
        var cycleDelay = 3.0;
        var seen = false;
        for (var i = 0; i < args.Length; i++)
        {
            var value = i + 1 < args.Length ? args[i + 1] : null;
            switch (args[i])
            {
                case "--instance" when value is not null:
                    instance = value;
                    i++;
                    seen = true;
                    break;
                case "--data" when value is not null:
                    data = value;
                    i++;
                    seen = true;
                    break;
                case "--theme" when value is not null && Enum.TryParse(value, true, out ThemeChoice parsed):
                    theme = parsed;
                    i++;
                    seen = true;
                    break;
                case "--page" when value is "library" or "updates" or "history" or "browse" or "settings":
                    page = value;
                    i++;
                    seen = true;
                    break;
                case "--details" when value is not null && long.TryParse(value, out var mangaId):
                    details = mangaId;
                    i++;
                    seen = true;
                    break;
                case "--reader" when value is not null:
                    {
                        var parts = value.Split(':');
                        if (parts.Length >= 3
                            && long.TryParse(parts[0], out var m)
                            && long.TryParse(parts[1], out var c)
                            && int.TryParse(parts[2], out var p))
                        {
                            readerManga = m;
                            readerChapter = c;
                            readerPage = Math.Max(0, p);
                            readerMode = parts.Length >= 4 ? ParseMode(parts[3]) : null;
                            seen = true;
                            i++;
                        }
                        break;
                    }
                case "--sheet" when value is "pair" or "onboarding":
                    sheet = value;
                    i++;
                    seen = true;
                    break;
                case "--size" when value is not null:
                    {
                        var parts = value.Split('x');
                        if (parts.Length == 2
                            && double.TryParse(parts[0], out var w) && w >= 200
                            && double.TryParse(parts[1], out var ht) && ht >= 200)
                        {
                            width = w;
                            height = ht;
                            seen = true;
                            i++;
                        }
                        break;
                    }
                case "--no-hub":
                    noHub = true;
                    seen = true;
                    break;
                case "--brushes":
                    brushesOutput = value is not null && !value.StartsWith("--") ? value : null;
                    if (brushesOutput is not null)
                    {
                        i++;
                    }
                    page = "brushes";
                    seen = true;
                    break;
                case "--cycle-theme-later":
                    cycleTheme = true;
                    if (value is not null && !value.StartsWith("--") && double.TryParse(value, out var cycleSeconds))
                    {
                        cycleDelay = cycleSeconds;
                        i++;
                    }
                    seen = true;
                    break;
                case "--dev-hover":
                    devHover = true;
                    seen = true;
                    break;
            }
        }
        if (!seen)
        {
            return;
        }
        Current = new DevHarness
        {
            Instance = instance,
            Paths = data is null ? null : new AppPaths(data),
            Theme = theme,
            Page = page,
            DetailsId = details,
            ReaderManga = readerManga,
            ReaderChapter = readerChapter,
            ReaderPage = readerPage,
            Mode = readerMode,
            Sheet = sheet,
            WindowWidth = width,
            WindowHeight = height,
            NoHub = noHub,
            DevHover = devHover,
            BrushesOutput = brushesOutput,
            CycleThemeLater = cycleTheme,
            CycleThemeDelay = cycleDelay,
        };
    }

    public static AppPaths ResolvePaths() => Current?.Paths ?? AppPaths.Default;

    public static void ApplyThemeOverride()
    {
        if (Current?.Theme is { } theme)
        {
            Services.Settings.Theme = theme;
        }
    }

    public static async Task RunAsync()
    {
        if (Current is not { } harness)
        {
            return;
        }
        var window = App.MainWindow;
        if (harness.Page == "brushes")
        {
            window.AddRootPage("brushes", () => new Pages.BrushesProbePage(harness.BrushesOutput));
        }
        await window.NavigateRootAsync(harness.Page ?? "library");
        if (Environment.GetEnvironmentVariable("TSUZUKI_PROBE") is { } probe)
        {
            foreach (var step in probe.Split(';', StringSplitOptions.RemoveEmptyEntries))
            {
                var probeParts = step.Split(':');
                if (probeParts.Length < 2 || probeParts[0] != "invokerow")
                {
                    continue;
                }
                var delay = probeParts.Length >= 3 && int.TryParse(probeParts[2], out var parsedDelay) ? parsedDelay : 1500;
                await Task.Delay(delay);
                var row = FindInvokableRow(window.Content, probeParts[1]);
                if (row is { } button)
                {
                    new Microsoft.UI.Xaml.Automation.Peers.ButtonAutomationPeer(button).Invoke();
                }
                else
                {
                    Log.Error("probe", new InvalidOperationException($"row not found: {probeParts[1]}"));
                }
            }
        }
        if (harness.Sheet == "pair")
        {
            DevicesPanel.Show();
        }
        else if (harness.Sheet == "onboarding" && !window.SheetOpen)
        {
            Onboarding.Show();
        }
        if (harness.DetailsId is { } details)
        {
            await AppNav.OpenDetailsAsync(details, null);
        }
        if (harness is { ReaderManga: { } manga, ReaderChapter: { } chapter })
        {
            if (harness.Mode is { } mode)
            {
                Services.Settings.ReaderMode = mode;
                Services.Settings.ReaderModeChosen = true;
            }
            await AppNav.OpenReaderAsync(manga, chapter, harness.ReaderPage, null);
        }
        if (harness.CycleThemeLater)
        {
            StartThemeCycle(window, harness.CycleThemeDelay);
        }
    }

    public static void StartThemeCycle(MainWindow window, double delaySeconds)
    {
        themeTimer = window.DispatcherQueue.CreateTimer();
        themeTimer.Interval = TimeSpan.FromSeconds(delaySeconds);
        themeTimer.IsRepeating = false;
        themeTimer.Tick += (_, _) =>
        {
            Services.Settings.Theme = Services.Settings.Theme == ThemeChoice.Light ? ThemeChoice.Dark : ThemeChoice.Light;
            AppHost.ApplyTheme();
        };
        themeTimer.Start();
    }

    private static Microsoft.UI.Xaml.Controls.Button? FindInvokableRow(Microsoft.UI.Xaml.DependencyObject? root, string nameText)
    {
        if (root is null)
        {
            return null;
        }
        if (root is Microsoft.UI.Xaml.Controls.Button { } button
            && Microsoft.UI.Xaml.Automation.AutomationProperties.GetName(button)?.Contains(nameText) == true)
        {
            return button;
        }
        var count = Microsoft.UI.Xaml.Media.VisualTreeHelper.GetChildrenCount(root);
        for (var i = 0; i < count; i++)
        {
            if (FindInvokableRow(Microsoft.UI.Xaml.Media.VisualTreeHelper.GetChild(root, i), nameText) is { } hit)
            {
                return hit;
            }
        }
        return null;
    }

    private static ReaderMode? ParseMode(string value) => value.ToLowerInvariant() switch
    {
        "single" => ReaderMode.Single,
        "double" => ReaderMode.Double,
        "strip" => ReaderMode.Webtoon,
        _ => null,
    };
}
