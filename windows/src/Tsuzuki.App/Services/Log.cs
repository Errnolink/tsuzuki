namespace Tsuzuki.App;

public static class Log
{
    private const long TrailLimit = 128 * 1024;
    private const int TrailKeep = 400;

    private static readonly object Gate = new();

    private static string Logs => DevHarness.ResolvePaths().Logs;
    private static string TrailPath => Path.Combine(Logs, "trail.log");
    private static string SessionPath => Path.Combine(Logs, "session.open");

    public static void Error(string where, Exception error) =>
        Write($"tsuzuki-{DateTime.Now:yyyy-MM-dd}.log", $"{DateTime.Now:HH:mm:ss.fff} {where}: {error}{Environment.NewLine}");

    public static void Trail(string what)
    {
        try
        {
            lock (Gate)
            {
                var info = new FileInfo(TrailPath);
                if (info.Exists && info.Length > TrailLimit)
                {
                    var tail = File.ReadAllLines(TrailPath).TakeLast(TrailKeep);
                    File.WriteAllLines(TrailPath, tail);
                }
                File.AppendAllText(TrailPath, $"{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff} {what}{Environment.NewLine}");
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }

    public static IReadOnlyList<string>? BeginSession()
    {
        try
        {
            lock (Gate)
            {
                Directory.CreateDirectory(Logs);
                IReadOnlyList<string>? crashed = null;
                if (File.Exists(SessionPath))
                {
                    crashed = File.Exists(TrailPath) ? [.. File.ReadAllLines(TrailPath).TakeLast(40)] : [];
                    File.AppendAllText(
                        Path.Combine(Logs, $"tsuzuki-{DateTime.Now:yyyy-MM-dd}.log"),
                        $"{DateTime.Now:HH:mm:ss.fff} previous session ended unexpectedly; last steps:{Environment.NewLine}{string.Join(Environment.NewLine, crashed)}{Environment.NewLine}");
                }
                File.WriteAllText(SessionPath, $"{Environment.ProcessId} {DateTime.Now:O} {typeof(Log).Assembly.GetName().Version}");
                return crashed;
            }
        }
        catch (IOException)
        {
            return null;
        }
        catch (UnauthorizedAccessException)
        {
            return null;
        }
    }

    public static void EndSession()
    {
        try
        {
            lock (Gate)
            {
                File.Delete(SessionPath);
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
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

    private static void Write(string file, string text)
    {
        try
        {
            lock (Gate)
            {
                File.AppendAllText(Path.Combine(Logs, file), text);
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
