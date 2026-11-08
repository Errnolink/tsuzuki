using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Downloads;
using Tsuzuki.Core.Extensions;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;
using Tsuzuki.Core.Sync;

namespace Tsuzuki.Core;

public sealed class Engine(
    AppPaths paths,
    ILibraryStore library,
    IMergeEditor merges,
    ISourceRegistry sources,
    DiskImageCache covers,
    DiskImageCache pages,
    ISyncHub hub,
    IBackupService backup,
    HttpClient http,
    DownloadQueue downloads,
    ExtensionManager extensions,
    Tsuzuki.Core.Tracking.TrackService tracking)
{
    public AppPaths Paths { get; } = paths;
    public ILibraryStore Library { get; } = library;
    public IMergeEditor Merges { get; } = merges;
    public ISourceRegistry Sources { get; } = sources;
    public DiskImageCache Covers { get; } = covers;
    public DiskImageCache Pages { get; } = pages;
    public ISyncHub Hub { get; } = hub;
    public IBackupService Backup { get; } = backup;
    public HttpClient Http { get; } = http;
    public DownloadQueue Downloads { get; } = downloads;
    public ExtensionManager Extensions { get; } = extensions;
    public Tsuzuki.Core.Tracking.TrackService Tracking { get; } = tracking;
    public LibraryRefresher Refresher { get; } = new(library, sources);
    public ListingCache Listings { get; } = new(Path.Combine(paths.Root, "cache", "listings"));
}
