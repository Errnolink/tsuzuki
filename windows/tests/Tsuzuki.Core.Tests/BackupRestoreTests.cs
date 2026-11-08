using Google.Protobuf;
using Tsuzuki.Core.Backup;
using Tsuzuki.Core.Library;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class BackupRestoreTests
{
    private const long MdSource = 2499283573021220255;

    [Fact]
    public async Task RestoreRaisesLibraryChangedWithTouchedManga()
    {
        var (db, store) = TestLib.NewLibrary();
        try
        {
            ChangeOrigin? origin = null;
            var raised = 0;
            store.Changed += (_, e) =>
            {
                origin = e.Origin;
                raised = e.MangaIds.Count;
            };
            var service = new BackupService(db, store);
            await using var stream = new MemoryStream(TestLib.Backup(
                TestLib.Manga(MdSource, "/manga/restored", "Restored", version: 3, modified: 5000,
                    TestLib.Chapter("/chapter/1", number: 1))).ToByteArray());
            var result = await service.RestoreAsync(stream);

            Assert.Equal(1, result.MangaAdded);
            Assert.Equal(ChangeOrigin.Local, origin);
            Assert.True(raised > 0);
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task TargetDiscoveryProbesOnceWhileFreshWithoutBlockingTheCaller()
    {
        var (db, _) = TestLib.NewLibrary();
        try
        {
            var probes = 0;
            using var gate = new ManualResetEventSlim();
            var service = new BackupService(db, () =>
            {
                gate.Wait();
                Interlocked.Increment(ref probes);
                return [new LocalFolderTarget(Path.GetTempPath())];
            });

            var pending = service.DiscoverTargetsAsync();
            Assert.False(pending.IsCompleted);
            gate.Set();
            var first = await pending;
            var second = await service.DiscoverTargetsAsync();

            Assert.Equal(1, probes);
            Assert.Same(first, second);
        }
        finally
        {
            db.Dispose();
        }
    }

    [Fact]
    public async Task PruneKeepsTheNewestOwnBackupsAndNeverTouchesOtherFiles()
    {
        var folder = Directory.CreateTempSubdirectory().FullName;
        try
        {
            var ct = TestContext.Current.CancellationToken;
            for (var i = 0; i < 4; i++)
            {
                var file = Path.Combine(folder, $"tsuzuki-pc_2026-01-0{i + 1}_10-00.tachibk");
                await File.WriteAllTextAsync(file, "x", ct);
                File.SetLastWriteTime(file, new DateTime(2026, 1, 1).AddDays(i));
            }
            var mihon = Path.Combine(folder, "eu.kanade.tachiyomi_2025-12-01.tachibk");
            await File.WriteAllTextAsync(mihon, "x", ct);
            File.SetLastWriteTime(mihon, new DateTime(2025, 12, 1));
            var target = new LocalFolderTarget(folder);

            await target.PruneAsync(1, ct);

            var kept = (await target.ListAsync(ct)).Select(f => f.Name).Order().ToList();
            Assert.Equal(["eu.kanade.tachiyomi_2025-12-01.tachibk", "tsuzuki-pc_2026-01-04_10-00.tachibk"], kept);
        }
        finally
        {
            Directory.Delete(folder, recursive: true);
        }
    }
}
