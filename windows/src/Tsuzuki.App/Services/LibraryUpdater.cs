using Tsuzuki.Core.Library;

namespace Tsuzuki.App;

public static class LibraryUpdater
{
    public static Task<UpdateRunResult> RunAsync() =>
        Services.Engine is { } engine ? engine.Refresher.RefreshLibraryAsync() : Task.FromResult(new UpdateRunResult(0, 0, 0));
}
