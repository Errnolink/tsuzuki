using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;

namespace Tsuzuki.App;

public static class Ui
{
    private static EventHandler? readerClosed;
    private static bool readerTracked;

    public static bool Enqueue(DispatcherQueue queue, Action work, string where = "ui") =>
        Enqueue(queue, DispatcherQueuePriority.Normal, work, where);

    public static bool Enqueue(DispatcherQueue queue, DispatcherQueuePriority priority, Action work, string where = "ui") =>
        queue.TryEnqueue(priority, () =>
        {
            try
            {
                work();
            }
            catch (Exception e)
            {
                Log.Error(where, e);
            }
        });

    public static bool Enqueue(DispatcherQueue queue, Func<Task> work, string where = "ui") =>
        Enqueue(queue, DispatcherQueuePriority.Normal, work, where);

    public static bool Enqueue(DispatcherQueue queue, DispatcherQueuePriority priority, Func<Task> work, string where = "ui") =>
        queue.TryEnqueue(priority, () => _ = Log.Guard(where, work));

    public static bool ReaderOpen => App.MainWindow?.ReaderLayer.Visibility == Visibility.Visible;

    public static event EventHandler? ReaderClosed
    {
        add
        {
            EnsureReaderTracked();
            readerClosed += value;
        }
        remove => readerClosed -= value;
    }

    private static void EnsureReaderTracked()
    {
        if (readerTracked || App.MainWindow is null) return;
        readerTracked = true;
        App.MainWindow.ReaderLayer.RegisterPropertyChangedCallback(UIElement.VisibilityProperty, (layer, _) =>
        {
            if (((UIElement)layer).Visibility != Visibility.Collapsed) return;
            try
            {
                readerClosed?.Invoke(null, EventArgs.Empty);
            }
            catch (Exception e)
            {
                Log.Error("reader-closed", e);
            }
        });
    }
}
