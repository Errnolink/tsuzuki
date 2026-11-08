using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.Presentation;
using Windows.Foundation;

namespace Tsuzuki.App.Navigation;

public sealed class DetailsTransition(UserControl page, long mangaId, TransitionOrigin origin)
{
    private CancellationTokenSource? running;

    public TileData? Tile => origin.Element is not { } e ? null
        : ItemsTree.FindAncestor<MangaTile>(e)?.Data ?? ItemsTree.FindAncestor<Next.CoverTile>(e)?.Data ?? ItemsTree.FindAncestor<Next.LibraryListRow>(e)?.Data;

    public CoverImage? Cover => origin.Element is not { } e ? null : e as CoverImage ?? ItemsTree.FindDescendant<CoverImage>(e);

    public double? KnownRatio => CoverImage.RatioOf(Cover?.Url) ?? CoverImage.RatioOf(Tile?.ThumbnailUrl);

    public bool FromLibrary => Tile?.InLibrary == true
        || ItemsTree.FindAncestor<Next.LibraryPage>(origin.Element) is not null;

    public void Skip() => running?.Cancel();

    public async Task EnterAsync(FrameworkElement? below, Task headerData, Action prepare, Func<Rect> headerCover, Func<bool, HeroParts> parts, Action<Task> load)
    {
        var host = App.MainWindow.Nav.Host;
        Motion.VisualOf(page).Opacity = 0;
        await Task.WhenAny(headerData, Task.Delay(16));
        prepare();
        await FirstLayoutAsync(120);
        var plan = ContainerTransform.PlanOpen(origin, host);
        if (!plan.Transform || below is null || page.ActualWidth <= 1 || page.ActualHeight <= 1)
        {
            var fade = ContainerTransform.Fade(page, opening: true, Begin());
            load(fade);
            await fade;
            return;
        }
        var cover = headerCover();
        var landsOnCover = TransitionPlanner.LandsOnCover(plan.Anchor);
        var target = landsOnCover ? cover : new Rect(0, 0, page.ActualWidth, page.ActualHeight);
        var tapped = ContainerTransform.BoxIn(origin.Element, host) is null ? null : origin.Element;
        if (tapped is not null)
        {
            tapped.Opacity = 0;
        }
        var flight = ContainerTransform.Expand(page, ContainerTransform.ToRect(plan.Anchor), target, ContainerTransform.RadiusOf(origin.Element), parts(landsOnCover), Begin());
        load(flight);
        await flight;
        if (tapped is not null)
        {
            tapped.Opacity = 1;
        }
        Settle(below);
    }

    public async Task ExitAsync(FrameworkElement? below, Rect headerCover, Func<bool, HeroParts> parts)
    {
        if (below is null)
        {
            return;
        }
        var host = App.MainWindow.Nav.Host;
        below.UpdateLayout();
        var covers = (below as ICoverHost)?.CoversOf(mangaId).ToList() ?? [];
        var plan = ContainerTransform.PlanReturn(covers, origin, host);
        if (plan.Target == ReturnTarget.Fade && covers.Count > 0 && !Motion.Reduced)
        {
            var wanted = covers.FirstOrDefault(c => ReferenceEquals(c, origin.Element)) ?? covers[0];
            ((UIElement?)ItemsTree.FindAncestor<SelectorItem>(wanted) ?? wanted).StartBringIntoView();
            below.UpdateLayout();
            plan = ContainerTransform.PlanReturn(covers, origin, host);
        }
        if (plan.Target == ReturnTarget.Fade || page.ActualWidth <= 1 || page.ActualHeight <= 1)
        {
            Settle(below);
            await ContainerTransform.Fade(page, opening: false, Begin());
            return;
        }
        var landing = plan.Target switch
        {
            ReturnTarget.HostCover => covers[plan.CoverIndex],
            ReturnTarget.Origin => origin.Element,
            _ => null,
        };
        var landsOnCover = TransitionPlanner.LandsOnCover(plan.Anchor);
        var source = landsOnCover ? headerCover : new Rect(0, 0, page.ActualWidth, page.ActualHeight);
        if (landing is not null)
        {
            landing.Opacity = 0;
        }
        await ContainerTransform.Collapse(page, ContainerTransform.ToRect(plan.Anchor), source, ContainerTransform.RadiusOf(landing ?? origin.Element), parts(landsOnCover), Begin());
        if (landing is not null)
        {
            landing.Opacity = 1;
        }
        Settle(below);
    }

    private CancellationToken Begin()
    {
        running?.Cancel();
        return (running = new CancellationTokenSource()).Token;
    }

    private async Task FirstLayoutAsync(int maxMs)
    {
        if (page.ActualWidth <= 1 || page.ActualHeight <= 1)
        {
            page.UpdateLayout();
        }
        if (page.ActualWidth > 1 && page.ActualHeight > 1)
        {
            return;
        }
        var laidOut = new TaskCompletionSource();
        void OnSize(object sender, SizeChangedEventArgs e)
        {
            if (e.NewSize.Width > 1 && e.NewSize.Height > 1)
            {
                laidOut.TrySetResult();
            }
        }
        page.SizeChanged += OnSize;
        try
        {
            await Task.WhenAny(laidOut.Task, Task.Delay(maxMs));
        }
        finally
        {
            page.SizeChanged -= OnSize;
        }
    }

    private static void Settle(FrameworkElement below)
    {
        var v = Motion.VisualOf(below);
        v.StopAnimation("Scale");
        v.StopAnimation("Opacity");
        v.Scale = System.Numerics.Vector3.One;
        v.Opacity = 1;
    }
}
