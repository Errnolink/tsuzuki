namespace Tsuzuki.App.Controls;

public sealed class NewestFirstGate(int slots)
{
    private readonly object gate = new();
    private readonly LinkedList<TaskCompletionSource> waiting = new();
    private readonly Dictionary<string, LinkedListNode<TaskCompletionSource>> byKey = [];
    private int free = slots;

    public Task WaitAsync(string key, bool urgent, CancellationToken ct)
    {
        TaskCompletionSource entry;
        LinkedListNode<TaskCompletionSource> node;
        lock (gate)
        {
            if (free > 0)
            {
                free--;
                return Task.CompletedTask;
            }
            entry = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            node = urgent ? waiting.AddFirst(entry) : waiting.AddLast(entry);
            byKey[key] = node;
        }
        if (ct.CanBeCanceled)
        {
            var registration = ct.Register(() =>
            {
                lock (gate)
                {
                    if (node.List is null)
                    {
                        return;
                    }
                    waiting.Remove(node);
                    byKey.Remove(key);
                }
                entry.TrySetCanceled(ct);
            });
            entry.Task.ContinueWith(_ => registration.Dispose(), TaskScheduler.Default);
        }
        return entry.Task;
    }

    public void Promote(string key)
    {
        lock (gate)
        {
            if (byKey.TryGetValue(key, out var node) && node.List is not null && waiting.First != node)
            {
                waiting.Remove(node);
                waiting.AddFirst(node);
            }
        }
    }

    public void Release()
    {
        TaskCompletionSource? next;
        lock (gate)
        {
            if (waiting.First is not { } first)
            {
                free++;
                return;
            }
            waiting.RemoveFirst();
            foreach (var pair in byKey)
            {
                if (pair.Value == first)
                {
                    byKey.Remove(pair.Key);
                    break;
                }
            }
            next = first.Value;
        }
        next.TrySetResult();
    }
}
