namespace Tsuzuki.Presentation;

public static class StripWheel
{
    private static readonly double[] Fractions = [0.25, 0.5, 1.0];
    public const int Notch = 120;

    public static (double Target, bool Handled) Next(double target, int step, int delta, double viewport, double maxOffset)
    {
        if (step < 1 || step > Fractions.Length || delta == 0 || viewport <= 0)
        {
            return (target, false);
        }
        var moved = target - viewport * Fractions[step - 1] * (delta / (double)Notch);
        return (Math.Clamp(moved, 0, Math.Max(0, maxOffset)), true);
    }
}
