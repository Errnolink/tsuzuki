using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Navigation;

public interface ITzPage
{
    void OnShown() { }
    void OnHidden() { }
}

public interface ITransitionPage
{
    Task EnterAsync(FrameworkElement? below);
    Task ExitAsync(FrameworkElement? below);
}

public interface ITransitionSkip
{
    void SkipTransition();
}

public sealed class NavHost(Grid host)
{

    private readonly Dictionary<string, FrameworkElement> roots = [];
    private readonly List<FrameworkElement> stack = [];
    private string? currentRoot;
    private bool busy;
    private Func<Task>? pending;
    private FrameworkElement? animating;
    private CancellationTokenSource? coreCts;

    public event EventHandler? Changed;

    public bool CanGoBack => stack.Count > 0;
    public string? CurrentRoot => currentRoot;
    public FrameworkElement? Top => stack.Count > 0 ? stack[^1] : currentRoot is null ? null : roots[currentRoot];
    public FrameworkElement? Below => stack.Count > 1 ? stack[^2] : stack.Count == 1 && currentRoot is not null ? roots[currentRoot] : null;
    public Grid Host => host;

    public FrameworkElement? Root(string key) => roots.GetValueOrDefault(key);
    public Task ShowRootAsync(string key, Func<FrameworkElement> factory)
    {
        Log.Trail("root " + key);
        return RunQueued(() => ShowRootCore(key, factory));
    }

    public Task PushAsync(FrameworkElement page)
    {
        Log.Trail("push " + page.GetType().FullName);
        return RunQueued(() => PushCore(page));
    }

    public Task PopAsync()
    {
        Log.Trail("pop");
        DevHarness.Mark("pop input");
        return RunQueued(PopCore);
    }

    private async Task RunQueued(Func<Task> run)
    {
        pending = run;
        if (busy)
        {
            coreCts?.Cancel();
            (animating as ITransitionSkip)?.SkipTransition();
            return;
        }
        busy = true;
        try
        {
            while (pending is { } next)
            {
                pending = null;
                coreCts?.Dispose();
                coreCts = new CancellationTokenSource();
                await next();
            }
        }
        finally
        {
            coreCts?.Dispose();
            coreCts = null;
            busy = false;
        }
    }

    private CancellationToken Token => coreCts?.Token ?? CancellationToken.None;

    private async Task ShowRootCore(string key, Func<FrameworkElement> factory)
    {
        var firstVisit = false;
        if (!roots.TryGetValue(key, out var page))
        {
            page = factory();
            roots[key] = page;
            page.Visibility = Visibility.Collapsed;
            host.Children.Insert(0, page);
            firstVisit = true;
        }

        var outgoing = Top;
        if (stack.Count > 0)
        {
            foreach (var pushed in stack)
            {
                host.Children.Remove(pushed);
                (pushed as ITzPage)?.OnHidden();
            }
            stack.Clear();
            if (currentRoot is not null)
            {
                Wake(roots[currentRoot]);
                roots[currentRoot].Visibility = Visibility.Collapsed;
            }
        }
        if (currentRoot == key && outgoing == page)
        {
            return;
        }

        var previousRoot = currentRoot is null ? null : roots[currentRoot];
        currentRoot = key;
        page.Visibility = Visibility.Visible;
        Changed?.Invoke(this, EventArgs.Empty);
        var span = Motion.Reduced ? Motion.Short3 : firstVisit ? Motion.Medium2 : Motion.Short4;
        (page as ITzPage)?.OnShown();
        if (previousRoot is not null && previousRoot != page)
        {
            (previousRoot as ITzPage)?.OnHidden();
            await Motion.FadeThrough(previousRoot, page, span, Token);
            previousRoot.Visibility = Visibility.Collapsed;
        }
        else
        {
            await Motion.FadeIn(page, span, interrupt: Token);
        }
    }

    private async Task PushCore(FrameworkElement page)
    {
        var below = Top;
        stack.Add(page);
        host.Children.Add(page);
        Changed?.Invoke(this, EventArgs.Empty);
        animating = page;
        if (page is ITransitionPage t)
        {
            await t.EnterAsync(below);
        }
        else
        {
            await Motion.SharedAxisX(below, page, forward: true, Token);
        }
        animating = null;
        if (below is not null)
        {
            Sleep(below);
            (below as ITzPage)?.OnHidden();
        }
        (page as ITzPage)?.OnShown();
    }

    private async Task PopCore()
    {
        if (stack.Count == 0)
        {
            return;
        }
        var page = stack[^1];
        page.IsHitTestVisible = false;
        stack.RemoveAt(stack.Count - 1);
        var below = Top;
        if (below is not null)
        {
            below.Visibility = Visibility.Visible;
            Wake(below);
        }
        Changed?.Invoke(this, EventArgs.Empty);
        (page as ITzPage)?.OnHidden();
        animating = page;
        if (page is ITransitionPage t)
        {
            await t.ExitAsync(below);
        }
        else if (below is not null)
        {
            await Motion.SharedAxisX(page, below, forward: false, Token);
        }
        animating = null;
        host.Children.Remove(page);
        (below as ITzPage)?.OnShown();
    }

    private static void Sleep(FrameworkElement page)
    {
        if (page is not TzPage sleeper)
        {
            page.Visibility = Visibility.Collapsed;
            return;
        }
        sleeper.IsEnabled = false;
        sleeper.IsHitTestVisible = false;
        sleeper.Asleep = true;
        Motion.VisualOf(sleeper).Opacity = 0.004f;
    }

    private static void Wake(FrameworkElement page)
    {
        if (page is TzPage sleeper)
        {
            sleeper.IsEnabled = true;
            sleeper.IsHitTestVisible = true;
            sleeper.Asleep = false;
        }
        Motion.VisualOf(page).Opacity = 1;
    }
}
