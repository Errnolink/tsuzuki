using System.Diagnostics;

namespace Tsuzuki.App;

public static class NetworkCheck
{
    public static async Task<string?> PublicNetworkNameAsync(string ip)
    {
        try
        {
            var script = $"$p = Get-NetIPAddress -IPAddress '{ip}' -ErrorAction SilentlyContinue | Get-NetConnectionProfile -ErrorAction SilentlyContinue; if ($p.NetworkCategory -eq 'Public') {{ $p.Name }}";
            var start = new ProcessStartInfo("powershell.exe", $"-NoProfile -NonInteractive -Command \"{script}\"")
            {
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            using var process = Process.Start(start);
            if (process is null)
            {
                return null;
            }
            var output = await process.StandardOutput.ReadToEndAsync();
            await process.WaitForExitAsync();
            var name = output.Trim();
            return name.Length == 0 ? null : name;
        }
        catch (System.ComponentModel.Win32Exception)
        {
            return null;
        }
    }
}
