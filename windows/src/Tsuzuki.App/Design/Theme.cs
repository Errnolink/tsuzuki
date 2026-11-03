using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace Tsuzuki.App.Design;

public static class Theme
{
    private static readonly Dictionary<string, Dictionary<string, (Color Color, double Opacity)>> Palettes = new();
    private static readonly Dictionary<string, TzLiveBrush> Live = new();
    private static string applied = "Dark";

    public static event EventHandler? Changed;

    public static void Apply(ElementTheme value)
    {
        CapturePalettes();
        applied = value == ElementTheme.Light ? "Light" : "Dark";
        foreach (var brush in Live.Values)
        {
            brush.Refresh();
        }
        Changed?.Invoke(null, EventArgs.Empty);
    }

    public static Brush Brush(string key) => Brush(key, null);

    public static Brush Brush(string key, string? paletteName = null)
    {
        var id = (paletteName ?? "") + "|" + key;
        if (!Live.TryGetValue(id, out var brush))
        {
            brush = new TzLiveBrush(key, paletteName);
            Live[id] = brush;
        }
        return brush;
    }

    public static Color Color(string key) => Lookup(key, applied).Color;

    internal static (Color Color, double Opacity) Lookup(string key, string? paletteName)
    {
        var name = paletteName ?? applied;
        if (Palettes.TryGetValue(name, out var table) && table.TryGetValue(key, out var found))
        {
            return found;
        }
        if (Application.Current.Resources[key] is SolidColorBrush brush)
        {
            return (brush.Color, brush.Opacity);
        }
        return (Microsoft.UI.Colors.Transparent, 1.0);
    }

    private static void CapturePalettes()
    {
        if (Palettes.Count > 0)
        {
            return;
        }
        foreach (var name in (string[])["Light", "Dark"])
        {
            var table = new Dictionary<string, (Color, double)>();
            foreach (var dictionary in Dictionaries())
            {
                if (dictionary.ThemeDictionaries.TryGetValue(name, out var found) && found is ResourceDictionary tokens)
                {
                    foreach (var (key, resource) in tokens)
                    {
                        if (key is string token && resource is SolidColorBrush brush)
                        {
                            table[token] = (brush.Color, brush.Opacity);
                        }
                    }
                }
            }
            Palettes[name] = table;
        }
    }

    private static IEnumerable<ResourceDictionary> Dictionaries()
    {
        var root = Application.Current.Resources;
        yield return root;
        foreach (var merged in root.MergedDictionaries)
        {
            yield return merged;
        }
    }
}
