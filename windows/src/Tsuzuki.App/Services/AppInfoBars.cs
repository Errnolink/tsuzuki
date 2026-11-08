using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;

namespace Tsuzuki.App;

public static class AppInfoBars
{
    private const int MaxShown = 3;
    private static readonly Dictionary<string, InfoBar> bars = [];

    public static void Show(string key, InfoBarSeverity severity, string title, string message, string? actionLabel = null, Action? action = null)
    {
        var host = App.MainWindow.InfoBars;
        if (!bars.TryGetValue(key, out var bar))
        {
            bar = new InfoBar { IsClosable = true, CornerRadius = new CornerRadius(8), HorizontalAlignment = HorizontalAlignment.Stretch };
            var owned = bar;
            bar.Closed += (_, _) => Remove(key, owned);
            bars[key] = bar;
            host.Children.Add(bar);
            while (host.Children.Count > MaxShown && host.Children[0] is InfoBar oldest)
            {
                Remove(bars.First(pair => pair.Value == oldest).Key, oldest);
            }
        }
        else if (bar.IsOpen && bar.Severity == severity && bar.Title == title && bar.Message == message && (bar.ActionButton as Button)?.Content as string == actionLabel)
        {
            return;
        }
        bar.Severity = severity;
        bar.Title = title;
        bar.Message = message;
        AutomationProperties.SetName(bar, $"{title}. {message}");
        if (actionLabel is not null && action is not null)
        {
            var button = new Button { Content = actionLabel };
            AutomationProperties.SetName(button, actionLabel);
            button.Click += (_, _) => action();
            bar.ActionButton = button;
        }
        else
        {
            bar.ActionButton = null;
        }
        bar.IsOpen = true;
    }

    public static void Dismiss(string key)
    {
        if (bars.TryGetValue(key, out var bar))
        {
            Remove(key, bar);
        }
    }

    private static void Remove(string key, InfoBar bar)
    {
        if (bars.TryGetValue(key, out var current) && current == bar)
        {
            bars.Remove(key);
        }
        App.MainWindow.InfoBars.Children.Remove(bar);
    }
}
