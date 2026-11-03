using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sources.MangaDex;
using Xunit;

namespace Tsuzuki.Core.Tests.Sources;

public sealed class SourceRegistryTests
{
    [Fact]
    public void Create_returns_one_source_per_language_with_extension_ids()
    {
        var sources = MangaDexSources.Create(new HttpClient(), new MangaDexOptions { Languages = ["en", "ja"] });

        Assert.Equal(2, sources.Count);
        Assert.Equal(2499283573021220255L, sources[0].Id);
        Assert.Equal("MangaDex", sources[0].Name);
        Assert.Equal("en", sources[0].Lang);
        Assert.Equal("ja", sources[1].Lang);
        Assert.NotEqual(sources[0].Id, sources[1].Id);
        Assert.True(sources[0].SupportsLatest);
    }

    [Fact]
    public void Registry_names_mangadex_sources_by_language()
    {
        var sources = MangaDexSources.Create(new HttpClient(), new MangaDexOptions { Languages = ["en"] });
        var registry = new SourceRegistry(sources, _ => null);

        Assert.Equal("MangaDex (fr)", registry.DisplayName(MangaDexIds.For("fr")));
        Assert.Equal("MangaDex (es-419)", registry.DisplayName(MangaDexIds.For("es-419")));
    }

    [Fact]
    public void Registry_names_the_known_merged_id()
    {
        var registry = new SourceRegistry([], _ => null);
        Assert.Equal("Merged", registry.DisplayName(6969));
    }

    [Fact]
    public void Registry_prefers_the_name_lookup_for_unknown_ids()
    {
        var registry = new SourceRegistry([], id => id == 123456789 ? "Phone Source" : null);
        Assert.Equal("Phone Source", registry.DisplayName(123456789));
    }

    [Fact]
    public void Registry_falls_back_to_the_raw_id()
    {
        var registry = new SourceRegistry([], _ => null);
        Assert.Equal("987654321", registry.DisplayName(987654321));
    }

    [Fact]
    public void Registry_get_returns_native_sources()
    {
        var sources = MangaDexSources.Create(new HttpClient(), new MangaDexOptions());
        var registry = new SourceRegistry(sources, _ => null);

        Assert.Equal(sources[0], registry.Get(2499283573021220255L));
        Assert.Null(registry.Get(MangaDexIds.For("ja")));
        Assert.Equal(1, registry.All.Count);
        Assert.Equal("MangaDex", registry.DisplayName(2499283573021220255L));
    }
}
