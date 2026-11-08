using System.Runtime.InteropServices;
using Microsoft.UI.Dispatching;

namespace Tsuzuki.App;

public sealed class NonPumpingSyncContext : SynchronizationContext
{
    private readonly DispatcherQueue queue;

    public NonPumpingSyncContext(DispatcherQueue queue)
    {
        this.queue = queue;
        SetWaitNotificationRequired();
    }

    public override void Post(SendOrPostCallback d, object? state) => queue.TryEnqueue(() =>
    {
        try
        {
            d(state);
        }
        catch (Exception e)
        {
            Log.Error("post", e);
        }
    });

    public override void Send(SendOrPostCallback d, object? state)
    {
        if (queue.HasThreadAccess)
        {
            d(state);
            return;
        }
        throw new NotSupportedException("Send to the UI thread is not supported");
    }

    public override SynchronizationContext CreateCopy() => this;

    public override int Wait(IntPtr[] waitHandles, bool waitAll, int millisecondsTimeout) =>
        WaitForMultipleObjectsEx(waitHandles.Length, waitHandles, waitAll, millisecondsTimeout, false);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern int WaitForMultipleObjectsEx(int nCount, IntPtr[] lpHandles, bool bWaitAll, int dwMilliseconds, bool bAlertable);
}
