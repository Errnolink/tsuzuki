using System.Text.Json;

namespace Tsuzuki.Core.Extensions;

public sealed class MemoStore
{
    private readonly string path;
    private readonly Dictionary<string, string> memos;
    private readonly object gate = new();
    private bool dirty;

    public MemoStore(string path)
    {
        this.path = path;
        try
        {
            memos = File.Exists(path) ? JsonSerializer.Deserialize(File.ReadAllText(path), ExtensionJson.Default.DictionaryStringString) ?? [] : [];
        }
        catch (Exception e) when (e is JsonException or IOException or UnauthorizedAccessException)
        {
            memos = [];
        }
    }

    public string? Get(string key)
    {
        lock (gate)
        {
            return memos.GetValueOrDefault(key);
        }
    }

    public void Set(string key, string memo)
    {
        lock (gate)
        {
            if (memos.TryGetValue(key, out var existing) && existing == memo)
            {
                return;
            }
            memos[key] = memo;
            dirty = true;
        }
    }

    public void Flush()
    {
        string json;
        lock (gate)
        {
            if (!dirty)
            {
                return;
            }
            dirty = false;
            json = JsonSerializer.Serialize(memos, ExtensionJson.Default.DictionaryStringString);
        }
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var temp = path + "." + Environment.CurrentManagedThreadId + ".tmp";
            File.WriteAllText(temp, json);
            File.Move(temp, path, overwrite: true);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            lock (gate)
            {
                dirty = true;
            }
        }
    }
}
