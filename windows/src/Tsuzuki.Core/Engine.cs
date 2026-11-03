using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sync;

namespace Tsuzuki.Core;

public sealed class Engine(
    AppPaths paths,
    ILibraryStore library,
    ISourceRegistry sources,
    DiskImageCache covers,
    DiskImageCache pages,
    ISyncHub hub,
    IBackupService backup,
    HttpClient http)
{
    public AppPaths Paths { get; } = paths;
    public ILibraryStore Library { get; } = library;
    public ISourceRegistry Sources { get; } = sources;
    public DiskImageCache Covers { get; } = covers;
    public DiskImageCache Pages { get; } = pages;
    public ISyncHub Hub { get; } = hub;
    public IBackupService Backup { get; } = backup;
    public HttpClient Http { get; } = http;
    public LibraryRefresher Refresher { get; } = new(library, sources);
}
