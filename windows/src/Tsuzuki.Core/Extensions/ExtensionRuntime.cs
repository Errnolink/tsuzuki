using System.IO.Compression;
using System.Security.Cryptography;

namespace Tsuzuki.Core.Extensions;

public sealed record RuntimeProgress(string Step, long Done, long Total);

public sealed class ExtensionRuntime
{
    public const string ServerJarUrl = "https://github.com/Suwayomi/Suwayomi-Server-preview/releases/download/v2.4.2476/Suwayomi-Server-v2.4.2476.jar";
    public const string ServerJarSha256 = "000af9c437d653896faa91f97f409e9c33fbb4e2e983d7a18f6de6d469e446f6";
    public const string JreUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip";
    public const string JreSha256 = "2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628";
    public const long DownloadBytes = 49106397L + 177601426L;

    private readonly string root;

    public ExtensionRuntime(string root, string hostJar)
    {
        var overrideRoot = Environment.GetEnvironmentVariable("TSUZUKI_EXTENSION_RUNTIME");
        this.root = string.IsNullOrEmpty(overrideRoot) ? root : overrideRoot;
        HostJar = hostJar;
        JavaPath = Path.Combine(this.root, "jre", "bin", "java.exe");
        ServerJar = Path.Combine(this.root, "suwayomi-server.jar");
    }

    public string JavaPath { get; }
    public string ServerJar { get; }
    public string HostJar { get; }

    public bool Installed => File.Exists(JavaPath) && File.Exists(ServerJar) && File.Exists(HostJar);

    public async Task InstallAsync(HttpClient http, IProgress<RuntimeProgress>? progress, CancellationToken ct)
    {
        Directory.CreateDirectory(root);
        if (!File.Exists(JavaPath))
        {
            var zip = Path.Combine(root, "jre.zip");
            await DownloadAsync(http, new Uri(JreUrl), zip, JreSha256, "Downloading the Java runtime", progress, ct).ConfigureAwait(false);
            var staging = Path.Combine(root, "jre-staging");
            if (Directory.Exists(staging))
            {
                Directory.Delete(staging, recursive: true);
            }
            progress?.Report(new RuntimeProgress("Unpacking the Java runtime", 0, 0));
            await Task.Run(() => ZipFile.ExtractToDirectory(zip, staging), ct).ConfigureAwait(false);
            var top = Directory.GetDirectories(staging).Single();
            var target = Path.Combine(root, "jre");
            if (Directory.Exists(target))
            {
                Directory.Delete(target, recursive: true);
            }
            Directory.Move(top, target);
            Directory.Delete(staging, recursive: true);
            File.Delete(zip);
        }
        if (!File.Exists(ServerJar))
        {
            await DownloadAsync(http, new Uri(ServerJarUrl), ServerJar, ServerJarSha256, "Downloading extension support", progress, ct).ConfigureAwait(false);
        }
    }

    private static async Task DownloadAsync(HttpClient http, Uri url, string path, string sha256, string step, IProgress<RuntimeProgress>? progress, CancellationToken ct)
    {
        var temp = path + ".part";
        using (var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false))
        {
            response.EnsureSuccessStatusCode();
            var total = response.Content.Headers.ContentLength ?? 0;
            await using var source = await response.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            await using var target = File.Create(temp);
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            var buffer = new byte[1 << 16];
            long done = 0;
            var lastReport = 0L;
            int read;
            while ((read = await source.ReadAsync(buffer, ct).ConfigureAwait(false)) > 0)
            {
                hash.AppendData(buffer, 0, read);
                await target.WriteAsync(buffer.AsMemory(0, read), ct).ConfigureAwait(false);
                done += read;
                if (done - lastReport > 1 << 20)
                {
                    lastReport = done;
                    progress?.Report(new RuntimeProgress(step, done, total));
                }
            }
            var actual = Convert.ToHexStringLower(hash.GetHashAndReset());
            if (!actual.Equals(sha256, StringComparison.OrdinalIgnoreCase))
            {
                target.Close();
                File.Delete(temp);
                throw new InvalidDataException($"The download from {url.Host} didn't match its expected checksum.");
            }
        }
        File.Move(temp, path, overwrite: true);
    }
}
