using Tsuzuki.Core;

namespace Tsuzuki.App;

public static class Services
{
    public static Engine? Engine { get; set; }
    public static AppSettings Settings { get; set; } = new();
}
