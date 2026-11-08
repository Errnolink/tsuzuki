using Tsuzuki.Core.Library;

namespace Tsuzuki.Presentation;

public sealed class ShellViewModel
{
    private readonly FeedViewModel<UpdateEntry> updates;

    public ShellViewModel(ILibraryStore library, IUiThread ui, Func<bool> readerOpen, TimeSpan? coalesce = null)
    {
        updates = new FeedViewModel<UpdateEntry>(library, store => store.GetUpdatesAsync(1000), ui, readerOpen, coalesce ?? TimeSpan.FromMilliseconds(700));
        updates.Changed += (_, _) =>
        {
            UnreadUpdates = updates.Items.Count(u => !u.Read);
            Changed?.Invoke(this, EventArgs.Empty);
        };
        updates.Activate();
    }

    public event EventHandler? Changed;

    public int UnreadUpdates { get; private set; }

    public void ReaderClosed() => updates.ReaderClosed();
}
