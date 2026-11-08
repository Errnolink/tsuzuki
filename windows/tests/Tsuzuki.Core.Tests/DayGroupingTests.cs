using Tsuzuki.Core.Library;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class DayGroupingTests
{
    private sealed record Read(string Title, string Day);

    [Fact]
    public void Two_titles_read_the_same_day_share_one_header()
    {
        var rows = DayGrouping.WithHeaders([new Read("Berserk", "Saturday"), new Read("Vagabond", "Saturday"), new Read("Monster", "Friday")], r => r.Day);

        Assert.Equal(["Saturday", new Read("Berserk", "Saturday"), new Read("Vagabond", "Saturday"), "Friday", new Read("Monster", "Friday")], rows);
    }
}
