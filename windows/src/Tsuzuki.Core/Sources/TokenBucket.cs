using System.Diagnostics;

namespace Tsuzuki.Core.Sources;

public sealed class TokenBucket
{
    private readonly object gate = new();
    private readonly double capacity;
    private readonly double refillPerSecond;
    private double tokens;
    private long lastRefill;

    public TokenBucket(int capacity, double tokensPerSecond)
    {
        this.capacity = capacity;
        refillPerSecond = tokensPerSecond;
        tokens = capacity;
        lastRefill = Stopwatch.GetTimestamp();
    }

    public async ValueTask AcquireAsync(CancellationToken ct = default)
    {
        while (true)
        {
            TimeSpan wait;
            lock (gate)
            {
                Refill();
                if (tokens >= 1)
                {
                    tokens -= 1;
                    return;
                }
                wait = TimeSpan.FromSeconds((1 - tokens) / refillPerSecond);
            }
            await Task.Delay(wait, ct).ConfigureAwait(false);
        }
    }

    private void Refill()
    {
        var now = Stopwatch.GetTimestamp();
        var elapsed = (now - lastRefill) / (double)Stopwatch.Frequency;
        if (elapsed <= 0)
        {
            return;
        }
        tokens = Math.Min(capacity, tokens + elapsed * refillPerSecond);
        lastRefill = now;
    }
}
