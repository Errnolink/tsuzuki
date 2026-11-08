using System.Diagnostics;
using Tsuzuki.Core.Sources;
using Xunit;

namespace Tsuzuki.Core.Tests;

public sealed class HostGateTests
{
    [Fact]
    public async Task Requests_to_one_site_are_spaced_and_capped_while_other_sites_go_straight_through()
    {
        var ct = TestContext.Current.CancellationToken;
        var gate = new HostGate(2, TimeSpan.FromMilliseconds(150));
        var site = new Uri("https://scans.example/page/1.jpg");
        var clock = Stopwatch.StartNew();

        using var first = await gate.EnterAsync(site, ct);
        var second = await gate.EnterAsync(site, ct);
        var secondAt = clock.ElapsedMilliseconds;
        var third = gate.EnterAsync(site, ct);
        var other = await gate.EnterAsync(new Uri("https://other.example/a.jpg"), ct);
        var otherAt = clock.ElapsedMilliseconds;

        Assert.True(secondAt >= 140, $"second request started after {secondAt} ms");
        Assert.True(otherAt - secondAt < 100, $"another site waited {otherAt - secondAt} ms");
        await Task.Delay(400, ct);
        Assert.False(third.IsCompleted);

        second.Dispose();
        (await third.WaitAsync(TimeSpan.FromSeconds(2), ct)).Dispose();
        other.Dispose();
    }
}
