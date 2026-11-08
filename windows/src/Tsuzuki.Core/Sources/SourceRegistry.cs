using Tsuzuki.Core.Sources.MangaDex;

namespace Tsuzuki.Core.Sources;

public sealed class SourceRegistry : ISourceRegistry
{
    private const long MergedId = 6969;

    private static readonly Dictionary<long, string> MangaDexLanguages =
        MangaDexIds.ExtensionLanguages.ToDictionary(MangaDexIds.For, lang => lang);

    private readonly Dictionary<long, ISource> byId;
    private readonly Func<long, string?> nameLookup;

    public SourceRegistry(IReadOnlyList<ISource> sources, Func<long, string?> nameLookup)
    {
        All = sources;
        byId = sources.ToDictionary(s => s.Id);
        this.nameLookup = nameLookup;
    }

    public IReadOnlyList<ISource> All { get; }

    public ISource? Get(long sourceId) => byId.GetValueOrDefault(sourceId);

    public bool IsReadable(long sourceId) => byId.ContainsKey(sourceId);

    public string DisplayName(long sourceId)
    {
        if (byId.TryGetValue(sourceId, out var native))
        {
            return native.Name;
        }
        if (sourceId == MergedId)
        {
            return "Merged";
        }
        if (MangaDexLanguages.TryGetValue(sourceId, out var lang))
        {
            return $"MangaDex ({lang})";
        }
        return nameLookup(sourceId) ?? sourceId.ToString();
    }
}
