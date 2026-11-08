using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Tsuzuki.Core.Extensions;

public sealed class ExtensionHostException(string message, string kind) : Exception(message)
{
    public string Kind { get; } = kind;
}

public sealed class ExtensionHost : IAsyncDisposable
{
    private static readonly TimeSpan StartTimeout = TimeSpan.FromSeconds(90);
    private static readonly TimeSpan CallTimeout = TimeSpan.FromSeconds(60);

    private readonly ExtensionRuntime runtime;
    private readonly string dataRoot;
    private readonly SemaphoreSlim startGate = new(1, 1);
    private readonly ConcurrentDictionary<long, TaskCompletionSource<JsonNode?>> pending = new();
    private readonly List<DateTimeOffset> crashes = [];
    private Process? process;
    private Task? ready;
    private long nextId;

    public ExtensionHost(ExtensionRuntime runtime, string dataRoot)
    {
        this.runtime = runtime;
        this.dataRoot = dataRoot;
    }

    public int Generation { get; private set; }

    public bool Running => process is { HasExited: false };

    public async Task<int> StartAsync(CancellationToken ct = default)
    {
        await EnsureStartedAsync(ct).ConfigureAwait(false);
        return Generation;
    }

    public async Task<JsonNode?> CallAsync(string op, JsonObject args, CancellationToken ct = default)
    {
        await EnsureStartedAsync(ct).ConfigureAwait(false);
        var id = Interlocked.Increment(ref nextId);
        var completion = new TaskCompletionSource<JsonNode?>(TaskCreationOptions.RunContinuationsAsynchronously);
        pending[id] = completion;
        var line = new JsonObject { ["id"] = id, ["op"] = op, ["args"] = args }.ToJsonString();
        try
        {
            var target = process ?? throw new ExtensionHostException("The extension runtime isn't running.", "runtime");
            lock (target)
            {
                target.StandardInput.WriteLine(line);
                target.StandardInput.Flush();
            }
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
            timeout.CancelAfter(op is "load" ? StartTimeout : CallTimeout);
            return await completion.Task.WaitAsync(timeout.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
            throw new HttpRequestException($"The extension took too long to answer ({op}).");
        }
        catch (IOException e)
        {
            throw new ExtensionHostException("The extension runtime stopped: " + e.Message, "runtime");
        }
        finally
        {
            pending.TryRemove(id, out _);
        }
    }

    private async Task EnsureStartedAsync(CancellationToken ct)
    {
        if (Running && ready is { IsCompletedSuccessfully: true })
        {
            return;
        }
        await startGate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (Running && ready is { IsCompletedSuccessfully: true })
            {
                return;
            }
            lock (crashes)
            {
                crashes.RemoveAll(c => DateTimeOffset.UtcNow - c > TimeSpan.FromMinutes(10));
                if (crashes.Count >= 3)
                {
                    throw new ExtensionHostException("The extension runtime keeps stopping. Restart Tsuzuki to try again.", "runtime");
                }
            }
            Start();
            await ready!.WaitAsync(StartTimeout, ct).ConfigureAwait(false);
        }
        finally
        {
            startGate.Release();
        }
    }

    private void Start()
    {
        Directory.CreateDirectory(dataRoot);
        var info = new ProcessStartInfo(runtime.JavaPath)
        {
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardInputEncoding = new UTF8Encoding(false),
            WorkingDirectory = dataRoot,
        };
        foreach (var arg in new[]
        {
            "-Xmx768m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
            "-cp", runtime.HostJar + Path.PathSeparator + runtime.ServerJar,
            "tsuzuki.host.Main", Path.Combine(dataRoot, "host"),
        })
        {
            info.ArgumentList.Add(arg);
        }
        var started = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var proc = new Process { StartInfo = info, EnableRaisingEvents = true };
        var logPath = Path.Combine(dataRoot, "host.log");
        var log = new StreamWriter(new FileStream(logPath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite)) { AutoFlush = true };
        proc.ErrorDataReceived += (_, e) =>
        {
            if (e.Data is { } text)
            {
                lock (log)
                {
                    log.WriteLine(text);
                }
            }
        };
        proc.OutputDataReceived += (_, e) => OnLine(e.Data, started);
        proc.Exited += (_, _) =>
        {
            lock (crashes)
            {
                crashes.Add(DateTimeOffset.UtcNow);
            }
            started.TrySetException(new ExtensionHostException("The extension runtime exited while starting. See extensions/host.log.", "runtime"));
            foreach (var call in pending.Values)
            {
                call.TrySetException(new ExtensionHostException("The extension runtime stopped.", "runtime"));
            }
            lock (log)
            {
                log.Dispose();
            }
        };
        if (!proc.Start())
        {
            throw new ExtensionHostException("The extension runtime couldn't start.", "runtime");
        }
        proc.BeginOutputReadLine();
        proc.BeginErrorReadLine();
        process = proc;
        ready = started.Task;
        Generation++;
    }

    private void OnLine(string? line, TaskCompletionSource started)
    {
        if (string.IsNullOrWhiteSpace(line) || line[0] != '{')
        {
            return;
        }
        JsonNode? message;
        try
        {
            message = JsonNode.Parse(line);
        }
        catch (JsonException)
        {
            return;
        }
        if (message is not JsonObject obj)
        {
            return;
        }
        var id = obj["id"]?.GetValue<long>() ?? -1;
        if (id == 0 && obj["ready"] is not null)
        {
            started.TrySetResult();
            return;
        }
        if (!pending.TryGetValue(id, out var completion))
        {
            return;
        }
        if (obj["error"] is { } error)
        {
            var kind = obj["kind"]?.GetValue<string>() ?? "source";
            var text = error.GetValue<string>();
            completion.TrySetException(kind == "network" ? new HttpRequestException(text) : new ExtensionHostException(text, kind));
            return;
        }
        completion.TrySetResult(obj["result"]?.DeepClone());
    }

    public async ValueTask DisposeAsync()
    {
        if (process is { HasExited: false } running)
        {
            try
            {
                running.StandardInput.Close();
                await running.WaitForExitAsync(new CancellationTokenSource(TimeSpan.FromSeconds(2)).Token).ConfigureAwait(false);
            }
            catch (Exception e) when (e is OperationCanceledException or IOException or InvalidOperationException)
            {
            }
            if (!running.HasExited)
            {
                running.Kill(entireProcessTree: true);
            }
        }
        process?.Dispose();
    }
}
