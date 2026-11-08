namespace Tsuzuki.Core.Library;

public interface IMergeEditor
{
    Task<long> MergeAsync(long mangaId, long candidateId, CancellationToken ct = default);
    Task SetInfoSourceAsync(long parentId, MergedRef child, CancellationToken ct = default);
    Task ReorderAsync(long parentId, IReadOnlyList<MergedRef> children, CancellationToken ct = default);
    Task RemoveAsync(long parentId, MergedRef child, CancellationToken ct = default);
    Task UnmergeAsync(long parentId, CancellationToken ct = default);
}
