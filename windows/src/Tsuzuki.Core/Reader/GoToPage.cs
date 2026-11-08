namespace Tsuzuki.Core.Reader;

public static class GoToPage
{
    public static int? Parse(string text, int pageCount)
    {
        if (pageCount <= 0)
        {
            return null;
        }
        var slash = text.IndexOf('/');
        var number = (slash >= 0 ? text[..slash] : text).Trim();
        if (!int.TryParse(number, System.Globalization.NumberStyles.None, System.Globalization.CultureInfo.InvariantCulture, out var page))
        {
            return null;
        }
        return Math.Clamp(page, 1, pageCount) - 1;
    }
}
