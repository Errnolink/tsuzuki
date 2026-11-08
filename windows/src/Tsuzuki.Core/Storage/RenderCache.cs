namespace Tsuzuki.Core.Storage;

internal sealed class RenderCache
{
    private readonly object _gate = new();
    private long _epoch;
    private (long Seq, long Epoch, byte[] Raw, byte[] Gzip, int Sent)? _entry;

    public long Epoch
    {
        get
        {
            lock (_gate) return _epoch;
        }
    }

    public bool TryGet(long seq, out byte[] raw, out byte[] gzip, out int sent)
    {
        lock (_gate)
        {
            if (_entry is { } e && e.Seq == seq && e.Epoch == _epoch)
            {
                raw = e.Raw;
                gzip = e.Gzip;
                sent = e.Sent;
                return true;
            }
        }
        raw = Array.Empty<byte>();
        gzip = Array.Empty<byte>();
        sent = 0;
        return false;
    }

    public void Fill(long seq, byte[] raw, byte[] gzip, int sent)
    {
        lock (_gate)
        {
            if (_entry is { } e && (e.Seq > seq || (e.Seq == seq && e.Epoch > _epoch))) return;
            _entry = (seq, _epoch, raw, gzip, sent);
        }
    }

    public void Invalidate()
    {
        lock (_gate)
        {
            _epoch++;
            _entry = null;
        }
    }
}
