using Tsuzuki.Presentation;
using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class StripWheelTests
{
    private const double Viewport = 800;
    private const double Max = 5000;

    [Fact]
    public void One_notch_moves_a_quarter_half_or_full_viewport_forward()
    {
        var (quarter, quarterHandled) = StripWheel.Next(0, 1, -120, Viewport, Max);
        var (half, halfHandled) = StripWheel.Next(0, 2, -120, Viewport, Max);
        var (full, fullHandled) = StripWheel.Next(0, 3, -120, Viewport, Max);

        Assert.True(quarterHandled && halfHandled && fullHandled);
        Assert.Equal(200, quarter);
        Assert.Equal(400, half);
        Assert.Equal(800, full);
    }

    [Fact]
    public void A_backward_notch_moves_back_toward_the_start()
    {
        var (target, handled) = StripWheel.Next(1000, 2, 120, Viewport, Max);

        Assert.True(handled);
        Assert.Equal(600, target);
    }

    [Fact]
    public void Quick_notches_chain_on_the_running_target_instead_of_losing_distance()
    {
        var target = 100.0;
        for (var i = 0; i < 3; i++)
        {
            var (next, handled) = StripWheel.Next(target, 2, -120, Viewport, Max);
            Assert.True(handled);
            target = next;
        }

        Assert.Equal(100 + 1.5 * Viewport, target);
    }

    [Fact]
    public void Targets_clamp_to_the_scrollable_range()
    {
        var (atEnd, _) = StripWheel.Next(Max - 100, 3, -120, Viewport, Max);
        var (atStart, _) = StripWheel.Next(100, 2, 120, Viewport, Max);

        Assert.Equal(Max, atEnd);
        Assert.Equal(0, atStart);
    }

    [Fact]
    public void Touchpad_deltas_that_are_not_full_notches_scale_proportionally()
    {
        var (target, handled) = StripWheel.Next(0, 1, -40, Viewport, Max);

        Assert.True(handled);
        Assert.Equal(Viewport * 0.25 / 3, target, 6);
    }

    [Fact]
    public void The_system_setting_and_empty_deltas_leave_the_wheel_to_the_platform()
    {
        var (system, systemHandled) = StripWheel.Next(0, 0, -120, Viewport, Max);
        var (noDelta, noDeltaHandled) = StripWheel.Next(0, 2, 0, Viewport, Max);

        Assert.False(systemHandled);
        Assert.Equal(0, system);
        Assert.False(noDeltaHandled);
        Assert.Equal(0, noDelta);
    }
}
