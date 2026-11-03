namespace Tsuzuki.App.Design;

public static class Format
{
    public static string Relative(long epochMs)
    {
        if (epochMs <= 0)
        {
            return "never";
        }
        var then = DateTimeOffset.FromUnixTimeMilliseconds(epochMs).ToLocalTime();
        var delta = DateTimeOffset.Now - then;
        if (delta.TotalSeconds < 60) return "just now";
        if (delta.TotalMinutes < 60) return $"{(int)delta.TotalMinutes} min ago";
        if (delta.TotalHours < 24 && then.Date == DateTime.Today) return $"{(int)delta.TotalHours} h ago";
        if (then.Date == DateTime.Today.AddDays(-1)) return "yesterday";
        if (delta.TotalDays < 7) return $"{(int)Math.Ceiling(delta.TotalDays)} days ago";
        if (then.Year == DateTime.Today.Year) return then.ToString("d MMM");
        return then.ToString("d MMM yyyy");
    }

    public static string Date(long epochMs) =>
        epochMs <= 0 ? string.Empty : DateTimeOffset.FromUnixTimeMilliseconds(epochMs).ToLocalTime().ToString("d MMM yyyy");

    public static string ChapterNumber(double number) => number >= 0 ? number.ToString("0.###") : "?";
}
