using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class CoverPaletteTests
{
    private static byte[] Cover(params (Rgb Color, int Pixels)[] regions)
    {
        var bytes = new List<byte>();
        foreach (var (color, pixels) in regions)
        {
            for (var i = 0; i < pixels; i++)
            {
                bytes.AddRange([color.B, color.G, color.R, 255]);
            }
        }
        return [.. bytes];
    }

    public static TheoryData<byte, byte, byte> Colours => new()
    {
        { 255, 106, 0 },
        { 255, 230, 40 },
        { 60, 220, 120 },
        { 40, 230, 240 },
        { 230, 40, 160 },
        { 120, 140, 255 },
        { 250, 245, 200 },
        { 90, 30, 30 },
    };

    [Theory]
    [MemberData(nameof(Colours))]
    public void White_text_on_the_card_and_its_buttons_stays_readable_for_any_cover(byte r, byte g, byte b)
    {
        var tint = CoverPalette.Tint(Cover((new Rgb(r, g, b), 400)))!;

        Assert.True(tint.Card.ContrastWith(Rgb.White) >= CoverPalette.TextContrast);
        Assert.True(tint.Accent.ContrastWith(Rgb.White) >= CoverPalette.TextContrast);
        Assert.True(tint.Glass.ContrastWith(Rgb.White) >= CoverPalette.TextContrast);
        Assert.True(tint.Glass.Luminance > tint.Card.Luminance);
    }

    [Theory]
    [MemberData(nameof(Colours))]
    public void The_card_keeps_the_cover_hue(byte r, byte g, byte b)
    {
        var source = new Rgb(r, g, b).ToHsv().H;

        var card = CoverPalette.Tint(Cover((new Rgb(r, g, b), 400)))!.Card.ToHsv().H;

        var delta = Math.Abs(source - card) % 360;
        Assert.True(Math.Min(delta, 360 - delta) <= 12, $"cover hue {source:F0}, card hue {card:F0}");
    }

    [Fact]
    public void An_orange_cover_gives_a_deep_card_like_the_store_card()
    {
        var card = CoverPalette.Tint(Cover((new Rgb(255, 106, 0), 400)))!.Card;

        var (_, saturation, value) = card.ToHsv();
        Assert.InRange(value, 0.3, 0.6);
        Assert.True(saturation >= 0.6);
    }

    [Fact]
    public void The_largest_coloured_area_wins_over_a_small_bright_accent()
    {
        var tint = CoverPalette.Tint(Cover((new Rgb(30, 160, 70), 300), (new Rgb(240, 30, 30), 40)))!;

        var hue = tint.Card.ToHsv().H;
        Assert.InRange(hue, 110, 160);
    }

    [Fact]
    public void A_hue_that_straddles_red_is_averaged_around_the_circle()
    {
        var tint = CoverPalette.Tint(Cover((new Rgb(230, 20, 40), 200), (new Rgb(230, 40, 20), 200)))!;

        var hue = tint.Card.ToHsv().H;
        Assert.True(hue < 15 || hue > 345, $"card hue {hue:F0}");
    }

    [Fact]
    public void A_black_and_white_cover_has_no_tint_so_the_page_keeps_its_theme()
    {
        var tint = CoverPalette.Tint(Cover((new Rgb(20, 20, 20), 200), (new Rgb(235, 235, 235), 200), (new Rgb(128, 128, 128), 100)));

        Assert.Null(tint);
    }
}
