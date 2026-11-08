using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.Core.Images;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.App.Reader;

public sealed class NextThumbnails
{
    private readonly Dictionary<int, Task<DecodedPage?>> loads = [];
    private readonly SemaphoreSlim gate = new(2);
    private CancellationTokenSource cts = new();
    private IReadOnlyList<PageRef> pages = [];
    private long source;

    public int DecodeWidth { get; set; } = 160;

    public void Load(IReadOnlyList<PageRef> list, long sourceId)
    {
        Clear();
        pages = list;
        source = sourceId;
    }

    public void Clear()
    {
        cts.Cancel();
        cts = new CancellationTokenSource();
        foreach (var load in loads.Values)
        {
            if (load.IsCompletedSuccessfully)
            {
                load.Result?.Dispose();
            }
        }
        loads.Clear();
        pages = [];
    }

    public Task<DecodedPage?> GetAsync(int page)
    {
        if (page < 0 || page >= pages.Count)
        {
            return Task.FromResult<DecodedPage?>(null);
        }
        if (!loads.TryGetValue(page, out var load))
        {
            loads[page] = load = LoadAsync(page, pages, source, DecodeWidth, cts.Token);
        }
        return load;
    }

    private async Task<DecodedPage?> LoadAsync(int page, IReadOnlyList<PageRef> list, long sourceId, int width, CancellationToken ct)
    {
        try
        {
            await gate.WaitAsync(ct);
            try
            {
                var path = await Services.Engine!.Pages.GetFileAsync(ImageRequest.Page(sourceId, list[page]), ImagePriority.Background, ct);
                var decoded = await PageDecoder.DecodeAsync(path, width, width * 4, ct);
                if (ct.IsCancellationRequested)
                {
                    decoded.Dispose();
                    return null;
                }
                return decoded;
            }
            finally
            {
                gate.Release();
            }
        }
        catch (OperationCanceledException)
        {
            return null;
        }
        catch (Exception e)
        {
            if (!ct.IsCancellationRequested)
            {
                Log.Error("reader thumbnail", e);
                Services.Engine?.Pages.Invalidate(ImageRequest.Page(sourceId, list[page]));
                loads.Remove(page);
            }
            return null;
        }
    }
}

public sealed partial class ThumbTile : Button
{
    private static readonly SolidColorBrush Placeholder = new(Windows.UI.Color.FromArgb(24, 255, 255, 255));
    private static readonly SolidColorBrush Clear = new(Microsoft.UI.Colors.Transparent);
    private static readonly SolidColorBrush Plate = new(Windows.UI.Color.FromArgb(170, 0, 0, 0));
    private readonly Image image = new() { Stretch = Stretch.UniformToFill, VerticalAlignment = VerticalAlignment.Top };
    private readonly Border ring = new() { CornerRadius = new CornerRadius(4), BorderThickness = new Thickness(2), BorderBrush = Clear, IsHitTestVisible = false };
    private readonly TextBlock number = new() { FontSize = 11, Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(230, 255, 255, 255)), HorizontalAlignment = HorizontalAlignment.Center };
    private readonly Border numberPlate = new()
    {
        Background = Plate,
        CornerRadius = new CornerRadius(4),
        Padding = new Thickness(5, 1, 5, 1),
        Margin = new Thickness(4),
        HorizontalAlignment = HorizontalAlignment.Right,
        VerticalAlignment = VerticalAlignment.Bottom,
        IsHitTestVisible = false,
    };
    private readonly Brush accent;
    private int binding;

    public ThumbTile(double width, double height, Brush accent)
    {
        this.accent = accent;
        Width = width;
        Height = height;
        Padding = new Thickness(0);
        BorderThickness = new Thickness(0);
        CornerRadius = new CornerRadius(4);
        Background = Placeholder;
        HorizontalContentAlignment = HorizontalAlignment.Stretch;
        VerticalContentAlignment = VerticalAlignment.Stretch;
        numberPlate.Child = number;
        Content = new Grid
        {
            CornerRadius = new CornerRadius(4),
            Children = { image, numberPlate, ring },
        };
    }

    public int Page { get; private set; } = -1;

    public void Bind(int page, NextThumbnails cache, bool current)
    {
        Page = page;
        var my = ++binding;
        image.Source = null;
        number.Text = (page + 1).ToString();
        AutomationProperties.SetName(this, $"Page {page + 1}");
        SetCurrent(current);
        _ = Log.Guard("reader thumbnail bind", async () =>
        {
            var decoded = await cache.GetAsync(page);
            if (decoded is not null && my == binding)
            {
                image.Source = decoded.Slices[0].Source;
            }
        });
    }

    public void Unbind()
    {
        binding++;
        Page = -1;
        image.Source = null;
    }

    public void SetCurrent(bool current)
    {
        ring.BorderBrush = current ? accent : Clear;
    }
}

public sealed partial class ThumbFactory(NextThumbnails cache, double width, double height, Action<int> pick) : IElementFactory
{
    private readonly Stack<ThumbTile> pool = new();
    private readonly Dictionary<int, ThumbTile> realized = [];
    private readonly Brush accent = new SolidColorBrush((Windows.UI.Color)Application.Current.Resources["SystemAccentColorLight2"]);
    private int first = -1;
    private int last = -1;

    public UIElement GetElement(ElementFactoryGetArgs args)
    {
        var page = (int)args.Data;
        ThumbTile tile;
        if (pool.Count > 0)
        {
            tile = pool.Pop();
        }
        else
        {
            tile = new ThumbTile(width, height, accent);
            tile.Click += (s, _) =>
            {
                if (s is ThumbTile { Page: >= 0 } t)
                {
                    pick(t.Page);
                }
            };
        }
        tile.Bind(page, cache, page >= first && page <= last);
        realized[page] = tile;
        return tile;
    }

    public void RecycleElement(ElementFactoryRecycleArgs args)
    {
        if (args.Element is ThumbTile tile)
        {
            if (realized.TryGetValue(tile.Page, out var owner) && owner == tile)
            {
                realized.Remove(tile.Page);
            }
            tile.Unbind();
            pool.Push(tile);
        }
    }

    public void SetCurrent(int from, int to)
    {
        first = from;
        last = to;
        foreach (var (page, tile) in realized)
        {
            tile.SetCurrent(page >= from && page <= to);
        }
    }

    public void Reset()
    {
        foreach (var tile in realized.Values)
        {
            tile.Unbind();
        }
        realized.Clear();
    }
}
