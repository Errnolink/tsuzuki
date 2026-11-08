using Xunit;

namespace Tsuzuki.Presentation.Tests;

public sealed class DragPoseTests
{
    private const double Page = 900;

    [Fact]
    public void The_page_follows_the_pointer_down_and_shrinks_as_it_goes()
    {
        var near = DragPose.At(40, Page);
        var far = DragPose.At(300, Page);

        Assert.True(far.Lift > near.Lift);
        Assert.True(far.Scale < near.Scale);
        Assert.True(near.Scale < 1);
    }

    [Fact]
    public void Shrinking_stops_at_three_quarters_however_far_the_pointer_goes()
    {
        Assert.Equal(DragPose.MinScale, DragPose.At(5000, Page).Scale, 3);
    }

    [Fact]
    public void A_short_slow_drag_springs_back()
    {
        Assert.False(DragPose.Dismisses(60, 100, Page));
    }

    [Fact]
    public void A_long_drag_dismisses_even_when_released_slowly()
    {
        Assert.True(DragPose.Dismisses(200, 0, Page));
    }

    [Fact]
    public void A_quick_flick_dismisses_before_the_distance_is_reached()
    {
        Assert.True(DragPose.Dismisses(60, 1500, Page));
    }

    [Fact]
    public void A_twitch_is_not_a_flick()
    {
        Assert.False(DragPose.Dismisses(10, 2000, Page));
    }
}
