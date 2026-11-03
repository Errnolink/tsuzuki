namespace Tsuzuki.Core.Sources.MangaDex;

public static class MangaDexSources
{
    public static IReadOnlyList<ISource> Create(HttpClient http, MangaDexOptions options)
        => Create(new MangaDexClient(http), options);

    public static IReadOnlyList<ISource> Create(MangaDexClient client, MangaDexOptions options)
        => options.Languages.Select(lang => (ISource)new MangaDexSource(client, options, lang)).ToList();
}

public sealed class SourceRegistry : ISourceRegistry
{
    private const long MergedId = 6969;

    private readonly IReadOnlyList<ISource> sources;
    private readonly Func<long, string?> nameLookup;
    private readonly IReadOnlyDictionary<long, string> mangaDexLanguages;

    public SourceRegistry(IReadOnlyList<ISource> sources, Func<long, string?> nameLookup)
    {
        this.sources = sources;
        this.nameLookup = nameLookup;
        var map = new Dictionary<long, string>();
        foreach (var lang in MangaDexIds.ExtensionLanguages)
        {
            map[MangaDexIds.For(lang)] = lang;
        }
        mangaDexLanguages = map;
    }

    public IReadOnlyList<ISource> All => sources;

    public ISource? Get(long sourceId) => sources.FirstOrDefault(s => s.Id == sourceId);

    public string DisplayName(long sourceId)
    {
        var native = Get(sourceId)?.Name;
        if (native is not null)
        {
            return native;
        }
        if (sourceId == MergedId)
        {
            return "Merged";
        }
        if (mangaDexLanguages.TryGetValue(sourceId, out var lang))
        {
            return $"MangaDex ({lang})";
        }
        return nameLookup(sourceId) ?? sourceId.ToString();
    }
}
