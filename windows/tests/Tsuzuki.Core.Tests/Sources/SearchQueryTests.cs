using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.WeebCentral;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class SearchQueryTests
{
    [Fact]
    public async Task Active_filter_count_ignores_defaults_and_counts_each_chosen_tag()
    {
        var filters = await new WeebCentralSource(new HttpClient(new RecordingHttp(_ => throw new InvalidOperationException())))
            .GetFiltersAsync(TestContext.Current.CancellationToken);

        Assert.Equal(0, SearchQuery.ForText("wind breaker").ActiveCount(filters));
        Assert.Equal(0, new SearchQuery("", new Dictionary<string, IReadOnlyList<string>>
        {
            ["sort"] = ["Best Match"],
            ["author"] = ["  "],
            ["included_status"] = [],
        }).ActiveCount(filters));
        Assert.Equal(5, new SearchQuery("", new Dictionary<string, IReadOnlyList<string>>
        {
            ["sort"] = ["Popularity"],
            ["author"] = ["NII Satoru"],
            ["included_status"] = ["Ongoing", "Hiatus"],
            ["included_tag"] = ["Action"],
            ["included_tag!"] = ["Sci-fi"],
        }).ActiveCount(filters));
    }
}
