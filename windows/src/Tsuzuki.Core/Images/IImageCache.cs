namespace Tsuzuki.Core.Images;

public enum ImagePriority
{
    Visible = 0,
    Prefetch = 1,
    Background = 2,
}

public interface IImageCache
{
    Task<string> GetFileAsync(Uri url, ImagePriority priority, CancellationToken ct = default);
    void Prefetch(IEnumerable<Uri> urls);
}
