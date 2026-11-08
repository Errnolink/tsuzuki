namespace Tsuzuki.Core.Backup;

public sealed record BackupFileInfo(string Name, long SizeBytes, DateTimeOffset CreatedAt);

public sealed record BackupResult(string Location, int MangaCount, long SizeBytes);

public sealed record RestoreResult(int MangaAdded, int MangaUpdated, int ChaptersUpdated, int CategoriesUpdated);

public interface IBackupTarget
{
    string Id { get; }
    string DisplayName { get; }
    string Location { get; }
    Task<string> WriteAsync(string fileName, Stream data, CancellationToken ct = default);
    Task<IReadOnlyList<BackupFileInfo>> ListAsync(CancellationToken ct = default);
    Task<Stream> OpenReadAsync(string fileName, CancellationToken ct = default);
    Task PruneAsync(int keep, CancellationToken ct = default);
}

public interface IBackupService
{
    Task<IReadOnlyList<IBackupTarget>> DiscoverTargetsAsync();
    Task<BackupResult> CreateAsync(IBackupTarget target, CancellationToken ct = default);
    Task<RestoreResult> RestoreAsync(Stream tachibk, CancellationToken ct = default);
}
