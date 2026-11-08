namespace Tsuzuki.Core.Sources;

public interface ISourceImageStreams
{
    Task<Stream> OpenImageAsync(Uri url, CancellationToken ct);
}
