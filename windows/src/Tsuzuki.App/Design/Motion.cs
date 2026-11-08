using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Hosting;
using Windows.UI.ViewManagement;

namespace Tsuzuki.App.Design;

public enum Curve
{
    Emphasized,
    EmphasizedDecelerate,
    EmphasizedAccelerate,
    Standard,
    StandardDecelerate,
    StandardAccelerate,
    Linear,
}

public static class Motion
{
    public static readonly TimeSpan Short2 = TimeSpan.FromMilliseconds(100);
    public static readonly TimeSpan Short3 = TimeSpan.FromMilliseconds(150);
    public static readonly TimeSpan Short4 = TimeSpan.FromMilliseconds(200);
    public static readonly TimeSpan Medium1 = TimeSpan.FromMilliseconds(250);
    public static readonly TimeSpan Medium2 = TimeSpan.FromMilliseconds(300);
    public static readonly TimeSpan Medium4 = TimeSpan.FromMilliseconds(400);
    public static readonly TimeSpan Long1 = TimeSpan.FromMilliseconds(450);
    public static readonly TimeSpan Long2 = TimeSpan.FromMilliseconds(500);
    public static readonly TimeSpan HeroOpen = TimeSpan.FromMilliseconds(210);
    public static readonly TimeSpan HeroBack = TimeSpan.FromMilliseconds(340);
    public const float SnappySpring = 7.43f;
    public const float SettlingSpring = 5.5f;

    private static readonly UISettings Settings = new();

    public static bool Reduced => !Settings.AnimationsEnabled;
    public static bool FluentTimings => Services.Settings.LabMotion;

    public static Visual VisualOf(UIElement element)
    {
        ElementCompositionPreview.SetIsTranslationEnabled(element, true);
        return ElementCompositionPreview.GetElementVisual(element);
    }

    public static CompositionEasingFunction Easing(Compositor c, Curve curve) => curve switch
    {
        Curve.EmphasizedDecelerate => c.CreateCubicBezierEasingFunction(new(0.05f, 0.7f), new(0.1f, 1f)),
        Curve.EmphasizedAccelerate => c.CreateCubicBezierEasingFunction(new(0.3f, 0f), new(0.8f, 0.15f)),
        Curve.Standard => c.CreateCubicBezierEasingFunction(new(0.2f, 0f), new(0f, 1f)),
        Curve.StandardDecelerate => c.CreateCubicBezierEasingFunction(new(0f, 0f), new(0f, 1f)),
        Curve.StandardAccelerate => c.CreateCubicBezierEasingFunction(new(0.3f, 0f), new(1f, 1f)),
        Curve.Linear => c.CreateLinearEasingFunction(),
        _ => c.CreateCubicBezierEasingFunction(new(0.2f, 0f), new(0f, 1f)),
    };

    private static void Keys(Compositor c, ScalarKeyFrameAnimation a, float from, float to, Curve curve)
    {
        a.InsertKeyFrame(0f, from);
        if (curve == Curve.Emphasized)
        {
            a.InsertKeyFrame(0.1666667f, from + (to - from) * 0.4f, c.CreateCubicBezierEasingFunction(new(0.3f, 0f), new(0.8f, 0.15f)));
            a.InsertKeyFrame(1f, to, c.CreateCubicBezierEasingFunction(new(0.05f, 0.7f), new(0.1f, 1f)));
        }
        else
        {
            a.InsertKeyFrame(1f, to, Easing(c, curve));
        }
    }

    private static void Keys(Compositor c, Vector3KeyFrameAnimation a, Vector3 from, Vector3 to, Curve curve)
    {
        a.InsertKeyFrame(0f, from);
        if (curve == Curve.Emphasized)
        {
            a.InsertKeyFrame(0.1666667f, from + (to - from) * 0.4f, c.CreateCubicBezierEasingFunction(new(0.3f, 0f), new(0.8f, 0.15f)));
            a.InsertKeyFrame(1f, to, c.CreateCubicBezierEasingFunction(new(0.05f, 0.7f), new(0.1f, 1f)));
        }
        else
        {
            a.InsertKeyFrame(1f, to, Easing(c, curve));
        }
    }

    private static void Keys(Compositor c, Vector2KeyFrameAnimation a, Vector2 from, Vector2 to, Curve curve)
    {
        a.InsertKeyFrame(0f, from);
        if (curve == Curve.Emphasized)
        {
            a.InsertKeyFrame(0.1666667f, from + (to - from) * 0.4f, c.CreateCubicBezierEasingFunction(new(0.3f, 0f), new(0.8f, 0.15f)));
            a.InsertKeyFrame(1f, to, c.CreateCubicBezierEasingFunction(new(0.05f, 0.7f), new(0.1f, 1f)));
        }
        else
        {
            a.InsertKeyFrame(1f, to, Easing(c, curve));
        }
    }

    public static ScalarKeyFrameAnimation Scalar(Compositor c, float from, float to, TimeSpan duration, Curve curve, TimeSpan delay = default)
    {
        var a = c.CreateScalarKeyFrameAnimation();
        Keys(c, a, from, to, curve);
        a.Duration = duration;
        a.DelayTime = delay;
        a.DelayBehavior = AnimationDelayBehavior.SetInitialValueBeforeDelay;
        return a;
    }

    public static ScalarKeyFrameAnimation CriticalSpring(Compositor c, float from, float to, TimeSpan duration, float stiffness = SnappySpring)
    {
        const int Samples = 24;
        var end = 1 - (1 + stiffness) * MathF.Exp(-stiffness);
        var a = c.CreateScalarKeyFrameAnimation();
        var linear = c.CreateLinearEasingFunction();
        a.InsertKeyFrame(0f, from);
        for (var i = 1; i <= Samples; i++)
        {
            var t = (float)i / Samples;
            var u = stiffness * t;
            var progress = (1 - (1 + u) * MathF.Exp(-u)) / end;
            a.InsertKeyFrame(t, from + (to - from) * progress, linear);
        }
        a.Duration = duration;
        return a;
    }

    public static Vector3KeyFrameAnimation Vector3(Compositor c, Vector3 from, Vector3 to, TimeSpan duration, Curve curve, TimeSpan delay = default)
    {
        var a = c.CreateVector3KeyFrameAnimation();
        Keys(c, a, from, to, curve);
        a.Duration = duration;
        a.DelayTime = delay;
        a.DelayBehavior = AnimationDelayBehavior.SetInitialValueBeforeDelay;
        return a;
    }

    public static Vector2KeyFrameAnimation Vector2(Compositor c, Vector2 from, Vector2 to, TimeSpan duration, Curve curve, TimeSpan delay = default)
    {
        var a = c.CreateVector2KeyFrameAnimation();
        Keys(c, a, from, to, curve);
        a.Duration = duration;
        a.DelayTime = delay;
        a.DelayBehavior = AnimationDelayBehavior.SetInitialValueBeforeDelay;
        return a;
    }

    public static Task Run(Compositor c, Action<CompositionScopedBatch> start)
    {
        var tcs = new TaskCompletionSource();
        var batch = c.CreateScopedBatch(CompositionBatchTypes.Animation);
        start(batch);
        batch.End();
        batch.Completed += (_, _) => tcs.TrySetResult();
        return tcs.Task;
    }

    public static Task FramesPresented(int frames, int maxMs)
    {
        var tcs = new TaskCompletionSource();
        var left = frames;
        void OnRendering(object? sender, object e)
        {
            if (--left > 0 && !tcs.Task.IsCompleted)
            {
                return;
            }
            Microsoft.UI.Xaml.Media.CompositionTarget.Rendering -= OnRendering;
            tcs.TrySetResult();
        }
        Microsoft.UI.Xaml.Media.CompositionTarget.Rendering += OnRendering;
        _ = Task.Delay(maxMs).ContinueWith(_ => tcs.TrySetResult(), TaskScheduler.Default);
        return tcs.Task;
    }

    public static async Task FadeIn(UIElement element, TimeSpan duration, float rise = 0, TimeSpan delay = default, CancellationToken interrupt = default)
    {
        var v = VisualOf(element);
        var c = v.Compositor;
        Task task;
        if (Reduced)
        {
            task = Run(c, _ => v.StartAnimation("Opacity", Scalar(c, 0, 1, Short3, Curve.Standard)));
        }
        else
        {
            task = Run(c, _ =>
            {
                v.StartAnimation("Opacity", Scalar(c, 0, 1, duration, Curve.StandardDecelerate, delay));
                if (rise != 0)
                {
                    v.StartAnimation("Translation", Vector3(c, new(0, rise, 0), System.Numerics.Vector3.Zero, duration, Curve.EmphasizedDecelerate, delay));
                }
            });
        }
        await Interrupt(task, interrupt, () =>
        {
            v.StopAnimation("Opacity");
            v.StopAnimation("Translation");
            v.Opacity = 1;
            v.Properties.InsertVector3("Translation", default);
        });
    }

    public static async Task FadeThrough(UIElement? outgoing, UIElement incoming, TimeSpan total, CancellationToken interrupt = default)
    {
        var vin = VisualOf(incoming);
        var c = vin.Compositor;
        var outDuration = TimeSpan.FromMilliseconds(total.TotalMilliseconds * 0.35);
        var inDuration = total - outDuration;
        var fluent = FluentTimings;
        Task task = Run(c, _ =>
        {
            if (outgoing is not null)
            {
                var vout = VisualOf(outgoing);
                vout.StartAnimation("Opacity", Scalar(c, 1, 0, outDuration, Curve.EmphasizedAccelerate));
            }
            vin.StartAnimation("Opacity", Scalar(c, 0, 1, inDuration, Curve.EmphasizedDecelerate, outDuration));
            if (Reduced)
            {
            }
            else if (fluent)
            {
                vin.StartAnimation("Translation", Vector3(c, new(0, 16, 0), System.Numerics.Vector3.Zero, inDuration, Curve.EmphasizedDecelerate, outDuration));
            }
            else
            {
                vin.CenterPoint = new((float)incoming.ActualSize.X / 2, (float)incoming.ActualSize.Y / 2, 0);
                vin.StartAnimation("Scale", Vector3(c, new(0.96f, 0.96f, 1), System.Numerics.Vector3.One, inDuration, Curve.EmphasizedDecelerate, outDuration));
            }
        });
        await Interrupt(task, interrupt, () =>
        {
            vin.StopAnimation("Opacity");
            vin.StopAnimation("Translation");
            vin.StopAnimation("Scale");
            vin.Opacity = 1;
            vin.Scale = System.Numerics.Vector3.One;
            vin.Properties.InsertVector3("Translation", default);
            if (outgoing is not null)
            {
                var vout = VisualOf(outgoing);
                vout.StopAnimation("Opacity");
                vout.Opacity = 0;
            }
        });
    }

    public static async Task SharedAxisX(UIElement? outgoing, UIElement incoming, bool forward, CancellationToken interrupt = default)
    {
        var vin = VisualOf(incoming);
        var c = vin.Compositor;
        var total = Reduced ? Short3 : FluentTimings ? Medium1 : Long1;
        var outDuration = TimeSpan.FromMilliseconds(total.TotalMilliseconds * 0.35);
        var inDuration = total - outDuration;
        var shift = Reduced ? 0f : 30f;
        Task task = Run(c, _ =>
        {
            if (outgoing is not null)
            {
                var vout = VisualOf(outgoing);
                vout.StartAnimation("Opacity", Scalar(c, 1, 0, outDuration, Curve.EmphasizedAccelerate));
                vout.StartAnimation("Translation", Vector3(c, System.Numerics.Vector3.Zero, new(forward ? -shift : shift, 0, 0), total, Curve.Emphasized));
            }
            vin.StartAnimation("Opacity", Scalar(c, 0, 1, inDuration, Curve.EmphasizedDecelerate, outDuration));
            vin.StartAnimation("Translation", Vector3(c, new(forward ? shift : -shift, 0, 0), System.Numerics.Vector3.Zero, total, Curve.Emphasized));
        });
        await Interrupt(task, interrupt, () =>
        {
            vin.StopAnimation("Opacity");
            vin.StopAnimation("Translation");
            vin.Opacity = 1;
            vin.Properties.InsertVector3("Translation", default);
            if (outgoing is not null)
            {
                var vout = VisualOf(outgoing);
                vout.StopAnimation("Opacity");
                vout.StopAnimation("Translation");
                vout.Opacity = 0;
                vout.Properties.InsertVector3("Translation", new(forward ? -shift : shift, 0, 0));
            }
        });
    }

    public static async Task Interrupt(Task task, CancellationToken interrupt, Action snap)
    {
        if (!interrupt.CanBeCanceled)
        {
            await task;
            return;
        }
        using var registration = interrupt.Register(snap);
        await task;
    }

    public static void Spring(UIElement element, string property, Vector3 target, float damping = 0.62f, float periodMs = 50)
    {
        var v = VisualOf(element);
        var c = v.Compositor;
        if (Reduced)
        {
            v.Properties.InsertVector3(property, target);
            v.StopAnimation(property);
            switch (property)
            {
                case "Scale": v.Scale = target; break;
                case "Translation": v.Properties.InsertVector3("Translation", target); break;
            }
            return;
        }
        var s = c.CreateSpringVector3Animation();
        s.FinalValue = target;
        s.DampingRatio = damping;
        s.Period = TimeSpan.FromMilliseconds(periodMs);
        v.StartAnimation(property, s);
    }

    public static void SpringScalar(UIElement element, string property, float target, float damping = 0.78f, float periodMs = 55)
    {
        var v = VisualOf(element);
        if (Reduced)
        {
            v.StopAnimation(property);
            if (property == "Opacity")
            {
                v.Opacity = target;
            }
            return;
        }
        var s = v.Compositor.CreateSpringScalarAnimation();
        s.FinalValue = target;
        s.DampingRatio = damping;
        s.Period = TimeSpan.FromMilliseconds(periodMs);
        v.StartAnimation(property, s);
    }

    public static void CenterOn(UIElement element)
    {
        var v = VisualOf(element);
        v.CenterPoint = new((float)element.ActualSize.X / 2, (float)element.ActualSize.Y / 2, 0);
    }

    public static async Task Stagger(IReadOnlyList<UIElement> elements, TimeSpan step, float rise = 14)
    {
        if (Reduced)
        {
            foreach (var e in elements)
            {
                _ = FadeIn(e, Short3);
            }
            return;
        }
        var tasks = new List<Task>(elements.Count);
        for (var i = 0; i < elements.Count; i++)
        {
            tasks.Add(FadeIn(elements[i], Medium2, rise, TimeSpan.FromMilliseconds(step.TotalMilliseconds * i)));
        }
        await Task.WhenAll(tasks);
    }
}
