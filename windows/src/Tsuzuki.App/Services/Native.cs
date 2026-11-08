namespace Tsuzuki.App;

public static class Native
{
    public static void Release(object? winrt)
    {
        if (winrt is WinRT.IWinRTObject projected)
        {
            projected.NativeObject.Dispose();
        }
    }
}
