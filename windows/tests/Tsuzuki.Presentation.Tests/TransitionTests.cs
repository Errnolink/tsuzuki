using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class TransitionTests
{
    private static readonly Box Window = new(0, 0, 1440, 900);
    private static readonly Box Tile = new(300, 200, 160, 240);
    private static readonly Box OtherTile = new(520, 200, 160, 240);
    private static readonly Box Offscreen = new(300, 1400, 160, 240);

    [Fact]
    public void Opening_grows_out_of_the_tapped_element()
    {
        var plan = TransitionPlanner.Open(reducedMotion: false, Window, Tile, remembered: new Box(0, 0, 10, 10));

        Assert.True(plan.Transform);
        Assert.Equal(Tile, plan.Anchor);
    }

    [Fact]
    public void Opening_from_an_overlay_that_has_closed_grows_out_of_where_it_was_tapped()
    {
        var palette = new Box(400, 120, 24, 36);

        var plan = TransitionPlanner.Open(reducedMotion: false, Window, tapped: null, remembered: palette);

        Assert.True(plan.Transform);
        Assert.Equal(palette, plan.Anchor);
    }

    [Fact]
    public void Opening_from_an_element_scrolled_out_of_view_fades_instead_of_flying_in_from_off_screen()
    {
        var plan = TransitionPlanner.Open(reducedMotion: false, Window, Offscreen, remembered: Tile);

        Assert.False(plan.Transform);
    }

    [Fact]
    public void Reduced_motion_fades_both_ways_instead_of_transforming()
    {
        Assert.False(TransitionPlanner.Open(reducedMotion: true, Window, Tile, Tile).Transform);
        Assert.Equal(ReturnTarget.Fade, TransitionPlanner.Return(reducedMotion: true, Window, [Tile], 0, Tile, Tile).Target);
        Assert.Equal(ReturnTarget.Fade, TransitionPlanner.Return(reducedMotion: true, Window, [], -1, Tile, Tile).Target);
    }

    [Fact]
    public void Back_lands_on_the_tapped_cover_when_the_title_shows_in_several_places()
    {
        var hero = new Box(40, 80, 200, 300);

        var plan = TransitionPlanner.Return(reducedMotion: false, Window, [Tile, hero], originCover: 1, origin: hero, remembered: hero);

        Assert.Equal(ReturnTarget.HostCover, plan.Target);
        Assert.Equal(1, plan.CoverIndex);
        Assert.Equal(hero, plan.Anchor);
    }

    [Fact]
    public void Back_follows_the_title_when_the_page_below_moved_it()
    {
        var plan = TransitionPlanner.Return(reducedMotion: false, Window, [OtherTile], originCover: -1, origin: Tile, remembered: Tile);

        Assert.Equal(ReturnTarget.HostCover, plan.Target);
        Assert.Equal(OtherTile, plan.Anchor);
    }

    [Fact]
    public void Back_never_lands_on_a_stale_spot_when_the_title_is_known_but_out_of_view()
    {
        var plan = TransitionPlanner.Return(reducedMotion: false, Window, [Offscreen], originCover: -1, origin: Tile, remembered: Tile);

        Assert.Equal(ReturnTarget.Fade, plan.Target);
    }

    [Fact]
    public void Back_returns_into_a_row_that_still_shows_and_into_a_closed_overlay_spot_otherwise()
    {
        var row = new Box(36, 300, 900, 56);

        Assert.Equal(ReturnTarget.Origin, TransitionPlanner.Return(reducedMotion: false, Window, [], -1, row, row).Target);
        Assert.Equal(ReturnTarget.Remembered, TransitionPlanner.Return(reducedMotion: false, Window, [], -1, origin: null, remembered: row).Target);
        Assert.Equal(ReturnTarget.Fade, TransitionPlanner.Return(reducedMotion: false, Window, [], -1, origin: null, remembered: null).Target);
    }

    [Fact]
    public void A_cover_lands_on_the_header_cover_and_a_text_row_or_button_grows_into_the_whole_page()
    {
        Assert.True(TransitionPlanner.LandsOnCover(Tile));
        Assert.True(TransitionPlanner.LandsOnCover(new Box(0, 0, 24, 36)));
        Assert.True(TransitionPlanner.LandsOnCover(new Box(0, 0, 300, 200)));
        Assert.False(TransitionPlanner.LandsOnCover(new Box(36, 300, 900, 56)));
        Assert.False(TransitionPlanner.LandsOnCover(new Box(600, 400, 140, 36)));
    }

    [Fact]
    public void Centred_header_puts_the_cover_in_the_middle_and_classic_keeps_it_at_the_left_edge()
    {
        var centred = DetailsHeaderLayout.Cover(DetailsHeaderStyle.Centred, pageWidth: 1200, padLeft: 36, padRight: 36, top: 28, ratio: 2.0 / 3);
        var classic = DetailsHeaderLayout.Cover(DetailsHeaderStyle.Classic, pageWidth: 1200, padLeft: 36, padRight: 36, top: 28, ratio: 2.0 / 3);

        Assert.Equal(1200 - centred.Right, centred.X, 3);
        Assert.Equal(36, classic.X);
        Assert.Equal(classic.Y, centred.Y);
    }

    [Theory]
    [InlineData(DetailsHeaderStyle.Centred)]
    [InlineData(DetailsHeaderStyle.Classic)]
    public void The_cover_keeps_its_own_shape_inside_the_header_box(DetailsHeaderStyle style)
    {
        foreach (var ratio in new[] { 0.5, 2.0 / 3, 0.75, 1.0, 1.6 })
        {
            var (width, height) = DetailsHeaderLayout.CoverSize(style, 1100, ratio);
            Assert.Equal(ratio, width / height, 3);
            Assert.True(style == DetailsHeaderStyle.Centred || (width <= 220 + 0.001 && height <= 220 * 1.5 + 0.001));
        }
        var portrait = DetailsHeaderLayout.CoverSize(style, 1100, 2.0 / 3);
        var landscape = DetailsHeaderLayout.CoverSize(style, 1100, 1.6);
        Assert.True(landscape.Width >= portrait.Width);
        Assert.True(landscape.Height < portrait.Height);
    }

    [Fact]
    public void Unknown_and_extreme_cover_ratios_fall_back_to_a_sane_shape()
    {
        Assert.Equal(2.0 / 3, CoverShape.Ratio(null), 6);
        Assert.Equal(2.0 / 3, CoverShape.Ratio(0), 6);
        Assert.Equal(2.0 / 3, CoverShape.Ratio(double.NaN), 6);
        Assert.Equal(0.45, CoverShape.Ratio(0.1), 6);
        Assert.Equal(1.8, CoverShape.Ratio(5), 6);
    }

    [Theory]
    [InlineData(1.0)]
    [InlineData(0.5)]
    [InlineData(1.6)]
    public void Flight_starts_as_the_tapped_tile_moves_the_cover_centre_to_centre_and_lands_in_the_slot(double slotRatio)
    {
        var tile = new Box(300, 420, 160, 240);
        var slotHeight = 330.0;
        var slot = new Box(560, 28, slotHeight * slotRatio, slotHeight);
        var flight = new HeroFlight(tile, slot, 1300, 860);

        Near(tile, flight.CardOnScreen(0));
        Near(tile, flight.VisibleCoverOnScreen(0));
        Assert.Equal(slotRatio, flight.CoverOnScreen(0).Width / flight.CoverOnScreen(0).Height, 6);
        for (var p = 0.0; p <= 1.0001; p += 0.05)
        {
            var card = flight.CardOnScreen(p);
            var cover = flight.CoverOnScreen(p);
            var visible = flight.VisibleCoverOnScreen(p);
            Assert.True(visible.X >= card.X - 1e-6 && visible.Y >= card.Y - 1e-6 && visible.Right <= card.Right + 1e-6 && visible.Bottom <= card.Bottom + 1e-6);
            Assert.True(visible.Width > 0 && visible.Height > 0);
            var expectedX = Lerp(tile.X + tile.Width / 2, slot.X + slot.Width / 2, p);
            var expectedY = Lerp(tile.Y + tile.Height / 2, slot.Y + slot.Height / 2, p);
            Assert.Equal(expectedX, cover.X + cover.Width / 2, 6);
            Assert.Equal(expectedY, cover.Y + cover.Height / 2, 6);
        }
        Near(new Box(0, 0, 1300, 860), flight.CardOnScreen(1));
        Near(slot, flight.CoverOnScreen(1));
    }

    private static double Lerp(double a, double b, double p) => a + (b - a) * p;

    private static void Near(Box expected, Box actual)
    {
        Assert.Equal(expected.X, actual.X, 6);
        Assert.Equal(expected.Y, actual.Y, 6);
        Assert.Equal(expected.Width, actual.Width, 6);
        Assert.Equal(expected.Height, actual.Height, 6);
    }
}
