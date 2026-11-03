namespace Tsuzuki.App;

public static class Log
{
    private static readonly object Gate = new();

    public static void Error(string where, Exception error)
    {
        try
        {
            var path = Path.Combine(DevHarness.ResolvePaths().Logs, $"tsuzuki-{DateTime.Now:yyyy-MM-dd}.log");
            lock (Gate)
            {
                File.AppendAllText(path, $"{DateTime.Now:HH:mm:ss.fff} {where}: {error}{Environment.NewLine}");
            }
        }
        catch (IOException)
        {
        }
    }


    public static async Task Guard(string where, Func<Task> work)
    {
        try
        {
            await work();
        }
        catch (Exception e)
        {
            Error(where, e);
        }
    }
}
