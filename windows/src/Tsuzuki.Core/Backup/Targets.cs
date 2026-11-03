using Microsoft.Win32;

namespace Tsuzuki.Core.Backup;

public sealed class LocalFolderTarget(string path) : IBackupTarget
{
    public string Id => "local";
    public string DisplayName => "This PC";
    public string Location => path;

    public async Task<string> WriteAsync(string fileName, Stream data, CancellationToken ct = default)
    {
        Directory.CreateDirectory(path);
        var full = System.IO.Path.Combine(path, fileName);
        await using var output = File.Create(full);
        await data.CopyToAsync(output, ct).ConfigureAwait(false);
        return full;
    }

    public Task<IReadOnlyList<BackupFileInfo>> ListAsync(CancellationToken ct = default) =>
        Task.FromResult(Enumerate().ToList() as IReadOnlyList<BackupFileInfo>);

    public Task<Stream> OpenReadAsync(string fileName, CancellationToken ct = default) =>
        Task.FromResult<Stream>(File.OpenRead(System.IO.Path.Combine(path, fileName)));

    public Task PruneAsync(int keep, CancellationToken ct = default)
    {
        var files = Enumerate().OrderByDescending(f => f.CreatedAt).ToList();
        foreach (var file in files.Skip(keep))
        {
            File.Delete(System.IO.Path.Combine(path, file.Name));
        }
        return Task.CompletedTask;
    }

    private IEnumerable<BackupFileInfo> Enumerate()
    {
        if (!Directory.Exists(path)) yield break;
        foreach (var file in Directory.EnumerateFiles(path, "*.tachibk"))
        {
            var info = new FileInfo(file);
            yield return new BackupFileInfo(info.Name, info.Length, info.LastWriteTime);
        }
    }
}

/// <summary>
/// Google Drive for desktop writes a "My Drive" folder at the root of a mounted drive
/// letter (commonly G:). The target stores backups under My Drive/Tsuzuki/Backups.
/// </summary>
public sealed class DriveForDesktopTarget : IBackupTarget
{
    private readonly string _myDriveRoot;

    private DriveForDesktopTarget(string myDriveRoot)
    {
        _myDriveRoot = myDriveRoot;
        Location = System.IO.Path.Combine(myDriveRoot, "Tsuzuki", "Backups");
    }

    public string Id => "drive";
    public string DisplayName => "Google Drive";
    public string Location { get; }

    public static DriveForDesktopTarget? Locate()
    {
        try
        {
            foreach (var drive in DriveInfo.GetDrives())
            {
                if (!drive.IsReady || drive.DriveType is DriveType.NoRootDirectory or DriveType.CDRom) continue;
                var candidate = System.IO.Path.Combine(drive.RootDirectory.FullName, "My Drive");
                if (Directory.Exists(candidate)) return new DriveForDesktopTarget(candidate);
            }
            foreach (var candidate in RegistryProbePaths())
            {
                if (Directory.Exists(candidate)) return new DriveForDesktopTarget(candidate);
            }
        }
        catch (IOException)
        {
        }
        return null;
    }

    private static List<string> RegistryProbePaths()
    {
        var found = new List<string>();
        var roots = new[] { @"Software\Google\DriveFS", @"Software\Google\Drive" };
        foreach (var rootName in roots)
        {
            RegistryKey? root = null;
            try
            {
                root = Registry.CurrentUser.OpenSubKey(rootName);
                if (root == null) continue;
                foreach (var subName in root.GetSubKeyNames())
                {
                    RegistryKey? sub = null;
                    try
                    {
                        sub = root.OpenSubKey(subName);
                        if (sub == null) continue;
                        foreach (var valueName in sub.GetValueNames())
                        {
                            if (sub.GetValue(valueName) is not string value) continue;
                            if (value.EndsWith("My Drive", StringComparison.OrdinalIgnoreCase))
                            {
                                found.Add(value);
                            }
                        }
                    }
                    finally
                    {
                        sub?.Dispose();
                    }
                }
            }
            catch (System.Security.SecurityException)
            {
            }
            finally
            {
                root?.Dispose();
            }
        }
        return found;
    }

    public async Task<string> WriteAsync(string fileName, Stream data, CancellationToken ct = default)
    {
        Directory.CreateDirectory(Location);
        var full = System.IO.Path.Combine(Location, fileName);
        await using var output = File.Create(full);
        await data.CopyToAsync(output, ct).ConfigureAwait(false);
        return full;
    }

    public Task<IReadOnlyList<BackupFileInfo>> ListAsync(CancellationToken ct = default) =>
        Task.FromResult(Enumerate().ToList() as IReadOnlyList<BackupFileInfo>);

    public Task<Stream> OpenReadAsync(string fileName, CancellationToken ct = default) =>
        Task.FromResult<Stream>(File.OpenRead(System.IO.Path.Combine(Location, fileName)));

    public Task PruneAsync(int keep, CancellationToken ct = default)
    {
        var files = Enumerate().OrderByDescending(f => f.CreatedAt).ToList();
        foreach (var file in files.Skip(keep))
        {
            File.Delete(System.IO.Path.Combine(Location, file.Name));
        }
        return Task.CompletedTask;
    }

    private IEnumerable<BackupFileInfo> Enumerate()
    {
        if (!Directory.Exists(Location)) yield break;
        foreach (var file in Directory.EnumerateFiles(Location, "*.tachibk"))
        {
            var info = new FileInfo(file);
            yield return new BackupFileInfo(info.Name, info.Length, info.LastWriteTime);
        }
    }
}
