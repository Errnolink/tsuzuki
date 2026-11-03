using Tsuzuki.Core.Storage;

namespace Tsuzuki.Core.Sync;

internal sealed record MergeRequest(IReadOnlyList<SyncItem> Items, IReadOnlyList<string> DeletedCategories);

internal sealed class MergeOutcome
{
    public List<SyncItem> Writes { get; } = new();
    public List<string> Tombstones { get; } = new();
    public Dictionary<string, List<string>> ReturnKeys { get; } = new();
    public bool ChangedForClient;

    public void ReturnItem(SyncItem current)
    {
        var key = current.Kind is SyncKind.Manga or SyncKind.Chapter or SyncKind.Category
            ? current.Kind.ToString()
            : ItemStore.SectionRowKind(current);
        if (!ReturnKeys.TryGetValue(key, out var keys))
        {
            keys = new List<string>();
            ReturnKeys[key] = keys;
        }
        keys.Add(current.Key);
        ChangedForClient = true;
    }
}

/// <summary>
/// Applies sync items to a stored-state view. Versioned kinds (manga, chapter, category)
/// resolve by version, then modification time, then keep the stored copy; section kinds
/// are won by the incoming payload. Absence never deletes: only category tombstones do.
/// </summary>
internal static class SyncMerge
{
    public static MergeOutcome Merge(ItemView view, MergeRequest request)
    {
        var outcome = new MergeOutcome();
        var remappedKeys = new Dictionary<string, string>();

        foreach (var key in request.DeletedCategories)
        {
            var current = view.Get(SyncKind.Category, key);
            if (current is { Deleted: false }) outcome.Tombstones.Add(key);
        }

        foreach (var incoming in request.Items.Where(i => i.Kind == SyncKind.Category))
        {
            var item = incoming;
            var current = view.Get(SyncKind.Category, item.Key);
            if (current == null && item.Name.Length > 0)
            {
                var byName = view.CategoryByName(item.Name);
                if (byName != null)
                {
                    remappedKeys[item.Key] = byName.Key;
                    item = item with { Key = byName.Key };
                    current = byName;
                }
            }

            switch (current)
            {
                case null:
                    outcome.Writes.Add(item);
                    break;
                case { Deleted: true }:
                    if (item.Version > current.Version) outcome.Writes.Add(item);
                    else outcome.ChangedForClient = true;
                    break;
                default:
                    ResolveVersioned(view, outcome, item, current);
                    break;
            }
        }

        foreach (var incoming in request.Items.Where(i => i.Kind != SyncKind.Category))
        {
            var item = incoming;
            if (item.Kind == SyncKind.Manga && remappedKeys.Count > 0 && item.Refs.Count > 0)
            {
                var refs = item.Refs.Select(r => remappedKeys.GetValueOrDefault(r, r)).ToList();
                item = item with { Refs = refs };
            }

            if (item.IsVersioned)
            {
                ResolveVersioned(view, outcome, item, view.Get(item.Kind, item.Key));
            }
            else
            {
                var current = view.Get(item.Kind, item.Key);
                if (current == null || !item.Payload.AsSpan().SequenceEqual(current.Payload))
                {
                    outcome.Writes.Add(item);
                }
            }
        }

        return outcome;
    }

    private static void ResolveVersioned(ItemView view, MergeOutcome outcome, SyncItem item, SyncItem? current)
    {
        switch (current)
        {
            case null:
            case { } existing when item.Version > existing.Version:
                outcome.Writes.Add(item);
                break;
            case { } existing when item.Version < existing.Version:
                outcome.ReturnItem(existing);
                break;
            case { } existing:
                ResolveTie(view, outcome, item, existing);
                break;
        }
    }

    private static void ResolveTie(ItemView view, MergeOutcome outcome, SyncItem item, SyncItem current)
    {
        if (view.SameContent(item, current)) return;
        if (item.ModifiedAt > current.ModifiedAt) outcome.Writes.Add(item);
        else if (item.ModifiedAt < current.ModifiedAt) outcome.ReturnItem(current);
        else
        {
            var cmp = string.CompareOrdinal(item.Origin ?? "", current.Origin ?? "");
            if (cmp > 0) outcome.Writes.Add(item);
            else outcome.ReturnItem(current);
        }
    }
}
