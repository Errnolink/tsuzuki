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
}
