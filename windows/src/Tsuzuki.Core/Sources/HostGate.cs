using System.Collections.Concurrent;

namespace Tsuzuki.Core.Sources;

public sealed class HostGate(int concurrency, TimeSpan spacing, TimeProvider? clock = null)
{
    private readonly ConcurrentDictionary<string, Host> hosts = new(StringComparer.OrdinalIgnoreCase);
    private readonly TimeProvider time = clock ?? TimeProvider.System;

    public static HostGate Polite { get; } = new(2, TimeSpan.FromMilliseconds(350));

    public async Task<IDisposable> EnterAsync(Uri url, CancellationToken ct)
    {
        var host = hosts.GetOrAdd(url.Host, _ => new Host(concurrency));
        await host.Slots.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            TimeSpan wait;
            lock (host)
            {
                var now = time.GetUtcNow();
                var start = host.NextStart > now ? host.NextStart : now;
                host.NextStart = start + spacing;
                wait = start - now;
            }
            if (wait > TimeSpan.Zero)
            {
                await Task.Delay(wait, time, ct).ConfigureAwait(false);
            }
            return new Lease(host.Slots);
        }
        catch
        {
            host.Slots.Release();
            throw;
        }
    }

    private sealed class Host(int concurrency)
    {
        public SemaphoreSlim Slots { get; } = new(concurrency);

        public DateTimeOffset NextStart { get; set; }
    }

    private sealed class Lease(SemaphoreSlim slots) : IDisposable
    {
        private int released;

        public void Dispose()
        {
            if (Interlocked.Exchange(ref released, 1) == 0)
            {
                slots.Release();
            }
        }
    }
}
