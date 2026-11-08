using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;

namespace Tsuzuki.App.Next;

internal static class ShellTheme
{
    public static Brush Brush(string key) => (Brush)Application.Current.Resources[key];

    public static Style Style(string key) => (Style)Application.Current.Resources[key];

    public static FontFamily Symbols => (FontFamily)Application.Current.Resources["SymbolThemeFontFamily"];

    public static FontIcon Glyph(string glyph, double size = 16) => new() { Glyph = glyph, FontFamily = Symbols, FontSize = size };

    public static TextBlock Text(string text, string style) => new() { Text = text, Style = Style(style), TextTrimming = TextTrimming.CharacterEllipsis };

    public static T Subtle<T>(T button) where T : ButtonBase
    {
        var transparent = new SolidColorBrush(Microsoft.UI.Colors.Transparent);
        button.Resources["ButtonBackground"] = transparent;
        button.Resources["ButtonBackgroundPointerOver"] = Brush("SubtleFillColorSecondaryBrush");
        button.Resources["ButtonBackgroundPressed"] = Brush("SubtleFillColorTertiaryBrush");
        button.Resources["ButtonBackgroundDisabled"] = transparent;
        button.Resources["ButtonBorderBrush"] = transparent;
        button.Resources["ButtonBorderBrushPointerOver"] = transparent;
        button.Resources["ButtonBorderBrushPressed"] = transparent;
        button.Resources["ButtonBorderBrushDisabled"] = transparent;
        button.Resources["ButtonForegroundPointerOver"] = Brush("TextFillColorPrimaryBrush");
        button.Resources["ButtonForegroundPressed"] = Brush("TextFillColorSecondaryBrush");
        button.Background = transparent;
        button.BorderThickness = new Thickness(0);
        return button;
    }
}
