namespace Tsuzuki.Core.Sync;

public sealed record HubStatus(bool Running, int Port, IReadOnlyList<string> Addresses, string? Error);

public sealed record PairingInfo(string PcName, string PrimaryUrl, IReadOnlyList<string> AllUrls, string ApiKey, string DeepLink);

public sealed record SyncDevice(string DeviceId, string Name, long LastSeenAt, long Cursor);

public sealed class DeviceSyncedEventArgs(SyncDevice device, int itemsReceived, int itemsSent) : EventArgs
{
    public SyncDevice Device { get; } = device;
    public int ItemsReceived { get; } = itemsReceived;
    public int ItemsSent { get; } = itemsSent;
}

public interface ISyncHub
{
    HubStatus Status { get; }
    Task StartAsync(CancellationToken ct = default);
    Task StopAsync();
    PairingInfo GetPairing();
    Task<PairingInfo> RegenerateKeyAsync(CancellationToken ct = default);
    Task<IReadOnlyList<SyncDevice>> GetDevicesAsync(CancellationToken ct = default);
    Task RemoveDeviceAsync(string deviceId, CancellationToken ct = default);
    event EventHandler<DeviceSyncedEventArgs>? DeviceSynced;
    event EventHandler? StatusChanged;
}
