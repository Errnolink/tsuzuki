namespace Tsuzuki.Presentation;

public readonly record struct DragPose(double Scale, double Lift)
{
    public const double MinScale = 0.75;
    private const double DismissFraction = 0.18;
    private const double FlingSpeed = 900;

    public static DragPose At(double offset, double pageHeight)
    {
        var travel = Math.Max(1, pageHeight * 0.6);
        var t = Math.Clamp(offset / travel, 0, 1);
        var scale = 1 - (1 - MinScale) * t;
        var lift = offset * 0.55 / (1 + offset / Math.Max(1, pageHeight));
        return new DragPose(scale, lift);
    }

    public static bool Dismisses(double offset, double velocity, double pageHeight) =>
        offset >= pageHeight * DismissFraction || (velocity >= FlingSpeed && offset > 24);
}
