using System.Xml.Linq;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class ThemeTokensTests
{
    private static readonly string[] RequiredKeys = ["TzBackground", "TzCard", "TzText", "TzStrokeStrong", "TzAccent"];

    private static string TokensPath()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            var candidate = Path.Combine(dir.FullName, "src", "Tsuzuki.App", "Design", "Tokens.xaml");
            if (File.Exists(candidate))
            {
                return candidate;
            }
            dir = dir.Parent;
        }
        throw new InvalidOperationException("Tokens.xaml not found above " + AppContext.BaseDirectory);
    }

    private static readonly XNamespace X = "http://schemas.microsoft.com/winfx/2006/xaml";

    private static Dictionary<string, (XElement Element, string Color)> Palette(string theme)
    {
        var document = XDocument.Load(TokensPath());
        var dictionary = document.Root!.Elements()
            .Single(e => e.Name.LocalName == "ResourceDictionary.ThemeDictionaries")
            .Elements()
            .Single(e => (string?)e.Attribute(X + "Key") == theme);
        var brushes = new Dictionary<string, (XElement, string)>();
        foreach (var brush in dictionary.Elements().Where(e => e.Name.LocalName == "SolidColorBrush"))
        {
            var key = (string?)brush.Attribute(X + "Key");
            var color = (string?)brush.Attribute("Color");
            if (key is not null && color is not null)
            {
                brushes[key] = (brush, color);
            }
        }
        return brushes;
    }

    private static (byte A, byte R, byte G, byte B) Parse(string hex)
    {
        hex = hex.TrimStart('#');
        if (hex.Length == 6)
        {
            hex = "FF" + hex;
        }
        return (Convert.ToByte(hex[0..2], 16), Convert.ToByte(hex[2..4], 16), Convert.ToByte(hex[4..6], 16), Convert.ToByte(hex[6..8], 16));
    }

    [Theory]
    [InlineData("TzBackground")]
    [InlineData("TzCard")]
    [InlineData("TzText")]
    [InlineData("TzStrokeStrong")]
    [InlineData("TzAccent")]
    public void LightAndDarkBothDefineTokenAndDiffer(string key)
    {
        var light = Palette("Light");
        var dark = Palette("Dark");
        Assert.True(light.ContainsKey(key), $"Light theme is missing {key}");
        Assert.True(dark.ContainsKey(key), $"Dark theme is missing {key}");
        var a = Parse(light[key].Color);
        var b = Parse(dark[key].Color);
        Assert.True(a.A > 0 && b.A > 0, $"{key} is fully transparent in a theme");
        Assert.True((a.R, a.G, a.B) != (b.R, b.G, b.B),
            $"{key} has identical RGB in Light and Dark: #{a.R:X2}{a.G:X2}{a.B:X2}");
    }

    [Fact]
    public void EveryBrushTokenExistsInBothThemes()
    {
        var light = Palette("Light");
        var dark = Palette("Dark");
        var lightOnly = light.Keys.Except(dark.Keys).ToList();
        var darkOnly = dark.Keys.Except(light.Keys).ToList();
        Assert.True(lightOnly.Count == 0 && darkOnly.Count == 0,
            $"Brush tokens missing from one theme. Light-only: [{string.Join(", ", lightOnly)}] Dark-only: [{string.Join(", ", darkOnly)}]");
    }
}
