namespace Tsuzuki.Core.Library;

public static class DayGrouping
{
    public static List<object> WithHeaders<T>(IEnumerable<T> items, Func<T, string> dayOf) where T : notnull
    {
        var result = new List<object>();
        string? currentDay = null;
        foreach (var item in items)
        {
            var day = dayOf(item);
            if (day != currentDay)
            {
                result.Add(day);
                currentDay = day;
            }
            result.Add(item);
        }
        return result;
    }
}
