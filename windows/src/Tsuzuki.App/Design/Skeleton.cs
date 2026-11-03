using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;

namespace Tsuzuki.App.Design;

public static class Skeleton
{
    public static void Pulse(IReadOnlyList<UIElement> elements, int stepMs = 90)
    {
        if (Motion.Reduced || !Services.Settings.LabSkeletons || elements.Count == 0)
        {
            return;
        }
        for (var i = 0; i < elements.Count; i++)
        {
            var element = elements[i];
            var v = Motion.VisualOf(element);
            var c = v.Compositor;
            var rest = (float)element.Opacity;
            var pulse = c.CreateScalarKeyFrameAnimation();
            pulse.InsertKeyFrame(0f, rest, c.CreateLinearEasingFunction());
            pulse.InsertKeyFrame(0.5f, MathF.Min(1f, rest * 2), c.CreateLinearEasingFunction());
            pulse.InsertKeyFrame(1f, rest, c.CreateLinearEasingFunction());
            pulse.Duration = TimeSpan.FromMilliseconds(1800);
            pulse.IterationBehavior = AnimationIterationBehavior.Forever;
            pulse.DelayTime = TimeSpan.FromMilliseconds(stepMs * i);
            pulse.DelayBehavior = AnimationDelayBehavior.SetInitialValueBeforeDelay;
            v.StartAnimation("Opacity", pulse);
        }
    }
}
