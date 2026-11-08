namespace Tsuzuki.Core.Sources;

public enum FilterKind
{
    Select,
    Multi,
    TriState,
    Text,
}

public sealed record FilterOption(string Label, string Value, string? Group = null);

public sealed record FilterSpec(string Key, string Label, FilterKind Kind, IReadOnlyList<FilterOption> Options)
{
    public IReadOnlyList<string> Default { get; init; } = [];

    public string ExcludeKey => Key + "!";
}

public sealed record SearchQuery(string Text, IReadOnlyDictionary<string, IReadOnlyList<string>> Values)
{
    public static SearchQuery Empty { get; } = new(string.Empty, new Dictionary<string, IReadOnlyList<string>>());

    public static SearchQuery ForText(string text) => new(text, Empty.Values);

    public IReadOnlyList<string> Get(FilterSpec spec) => Values.TryGetValue(spec.Key, out var chosen) ? chosen : spec.Default;

    public IReadOnlyList<string> Excluded(FilterSpec spec) => Values.TryGetValue(spec.ExcludeKey, out var chosen) ? chosen : [];

    public string Single(FilterSpec spec) => Get(spec).FirstOrDefault() ?? spec.Options.FirstOrDefault()?.Value ?? string.Empty;

    public int ActiveCount(IReadOnlyList<FilterSpec> specs)
    {
        var count = 0;
        foreach (var spec in specs)
        {
            switch (spec.Kind)
            {
                case FilterKind.TriState:
                    count += Get(spec).Count + Excluded(spec).Count;
                    break;
                case FilterKind.Select:
                    count += Single(spec) == (spec.Default.FirstOrDefault() ?? spec.Options.FirstOrDefault()?.Value ?? string.Empty) ? 0 : 1;
                    break;
                case FilterKind.Text:
                    count += string.IsNullOrWhiteSpace(Get(spec).FirstOrDefault()) ? 0 : 1;
                    break;
                default:
                    count += Get(spec).ToHashSet(StringComparer.Ordinal).SetEquals(spec.Default) ? 0 : 1;
                    break;
            }
        }
        return count;
    }
}
