using Tsuzuki.Core.Sources;

namespace Tsuzuki.Core.Images;

public enum ImagePriority
{
    Visible = 0,
    Prefetch = 1,
    Background = 2,
}

public sealed record ImageRequest(Uri Url, string Key, long Source)
{
    public static ImageRequest Page(long source, PageRef page) => new(page.ImageUrl, page.CacheKey, source);

    public static ImageRequest Cover(long source, Uri url) => new(url, url.AbsoluteUri, source);
}

public interface IImageCache
{
    Task<string> GetFileAsync(ImageRequest image, ImagePriority priority, CancellationToken ct = default);
    void Prefetch(IEnumerable<ImageRequest> images);
    void Invalidate(ImageRequest request);
}
