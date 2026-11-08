namespace Tsuzuki.Core.Sources.MangaDex;

public static class MangaDexSources
{
    public static IReadOnlyList<ISource> Create(HttpClient http, MangaDexOptions options)
        => Create(new MangaDexClient(http), options);

    public static IReadOnlyList<ISource> Create(MangaDexClient client, MangaDexOptions options)
        => options.Languages.Select(lang => (ISource)new MangaDexSource(client, options, lang)).ToList();
}
