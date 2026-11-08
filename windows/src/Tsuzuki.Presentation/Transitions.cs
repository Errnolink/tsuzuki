namespace Tsuzuki.Presentation;

public enum DetailsHeaderStyle
{
    Centred,
    Classic,
}

public readonly record struct Box(double X, double Y, double Width, double Height)
{
    public double Right => X + Width;

    public double Bottom => Y + Height;

    public bool IsVisibleIn(Box viewport) =>
        Width > 0 && Height > 0 && Right > viewport.X && X < viewport.Right && Bottom > viewport.Y && Y < viewport.Bottom;
}

public readonly record struct OpenPlan(bool Transform, Box Anchor);

public enum ReturnTarget
{
    Fade,
    HostCover,
    Origin,
    Remembered,
}

public readonly record struct ReturnPlan(ReturnTarget Target, Box Anchor, int CoverIndex = -1);

public static class TransitionPlanner
{
    public static OpenPlan Open(bool reducedMotion, Box viewport, Box? tapped, Box? remembered)
    {
        if (reducedMotion)
        {
            return default;
        }
        if (tapped is { } shown && shown.IsVisibleIn(viewport))
        {
            return new OpenPlan(true, shown);
        }
        if (tapped is null && remembered is { } gone && gone.IsVisibleIn(viewport))
        {
            return new OpenPlan(true, gone);
        }
        return default;
    }

    public static ReturnPlan Return(bool reducedMotion, Box viewport, IReadOnlyList<Box> hostCovers, int originCover, Box? origin, Box? remembered)
    {
        if (reducedMotion)
        {
            return default;
        }
        if (hostCovers.Count > 0)
        {
            if (originCover >= 0 && originCover < hostCovers.Count && hostCovers[originCover].IsVisibleIn(viewport))
            {
                return new ReturnPlan(ReturnTarget.HostCover, hostCovers[originCover], originCover);
            }
            for (var i = 0; i < hostCovers.Count; i++)
            {
                if (hostCovers[i].IsVisibleIn(viewport))
                {
                    return new ReturnPlan(ReturnTarget.HostCover, hostCovers[i], i);
                }
            }
            return default;
        }
        if (origin is { } shown)
        {
            return shown.IsVisibleIn(viewport) ? new ReturnPlan(ReturnTarget.Origin, shown) : default;
        }
        if (remembered is { } gone && gone.IsVisibleIn(viewport))
        {
            return new ReturnPlan(ReturnTarget.Remembered, gone);
        }
        return default;
    }

    public static bool LandsOnCover(Box anchor)
    {
        if (anchor.Width <= 0 || anchor.Height <= 0)
        {
            return false;
        }
        var aspect = anchor.Width / anchor.Height;
        return aspect >= CoverShape.Narrowest && aspect <= CoverShape.Widest;
    }
}

public static class CoverShape
{
    public const double Fallback = 2.0 / 3;
    public const double Narrowest = 0.45;
    public const double Widest = 1.8;

    public static double Ratio(double? known) =>
        known is { } r && r > 0 && double.IsFinite(r) ? Math.Clamp(r, Narrowest, Widest) : Fallback;
}

public static class DetailsHeaderLayout
{
    public static (double Width, double Height) CoverSize(DetailsHeaderStyle style, double contentWidth, double? ratio)
    {
        var r = CoverShape.Ratio(ratio);
        var (boxWidth, maxHeight) = style == DetailsHeaderStyle.Centred
            ? (Math.Min(contentWidth * 0.78, 280), Math.Min(contentWidth * 0.93, 360))
            : (220d, 330d);
        var width = boxWidth;
        var height = width / r;
        if (height > maxHeight)
        {
            height = maxHeight;
            width = height * r;
        }
        return (width, height);
    }

    public static Box Cover(DetailsHeaderStyle style, double pageWidth, double padLeft, double padRight, double top, double? ratio)
    {
        var content = Math.Max(0, pageWidth - padLeft - padRight);
        var (width, height) = CoverSize(style, content, ratio);
        var x = style == DetailsHeaderStyle.Centred ? padLeft + Math.Max(0, content - width) / 2 : padLeft;
        return new Box(x, top, width, height);
    }
}

public sealed class HeroFlight
{
    public HeroFlight(Box tile, Box slot, double pageWidth, double pageHeight)
    {
        Tile = tile;
        Slot = slot;
        Page = new Box(0, 0, pageWidth, pageHeight);
        var tileAspect = tile.Width / tile.Height;
        var width = slot.Width / slot.Height > tileAspect ? slot.Height * tileAspect : slot.Width;
        var height = width / tileAspect;
        StartCard = new Box(slot.X + (slot.Width - width) / 2, slot.Y + (slot.Height - height) / 2, width, height);
        StartScale = tile.Width / width;
    }

    public Box Tile { get; }

    public Box Slot { get; }

    public Box Page { get; }

    public Box StartCard { get; }

    public double StartScale { get; }

    public double ScaleAt(double p) => Lerp(StartScale, 1, p);

    public (double X, double Y) TranslationAt(double p)
    {
        var s = ScaleAt(p);
        var slotX = Slot.X + Slot.Width / 2;
        var slotY = Slot.Y + Slot.Height / 2;
        return (Lerp(Tile.X + Tile.Width / 2, slotX, p) - s * slotX, Lerp(Tile.Y + Tile.Height / 2, slotY, p) - s * slotY);
    }

    public Box CardAt(double p) => new(Lerp(StartCard.X, Page.X, p), Lerp(StartCard.Y, Page.Y, p), Lerp(StartCard.Width, Page.Width, p), Lerp(StartCard.Height, Page.Height, p));

    public Box CardOnScreen(double p) => OnScreen(CardAt(p), p);

    public Box CoverOnScreen(double p) => OnScreen(Slot, p);

    public Box VisibleCoverOnScreen(double p)
    {
        var card = CardOnScreen(p);
        var cover = CoverOnScreen(p);
        var left = Math.Max(card.X, cover.X);
        var top = Math.Max(card.Y, cover.Y);
        return new Box(left, top, Math.Max(0, Math.Min(card.Right, cover.Right) - left), Math.Max(0, Math.Min(card.Bottom, cover.Bottom) - top));
    }

    private Box OnScreen(Box local, double p)
    {
        var s = ScaleAt(p);
        var (x, y) = TranslationAt(p);
        return new Box(local.X * s + x, local.Y * s + y, local.Width * s, local.Height * s);
    }

    private static double Lerp(double a, double b, double p) => a + (b - a) * p;
}
