using Tsuzuki.Core.Tracking;
using Windows.Security.Credentials;

namespace Tsuzuki.App;

public sealed class VaultCredentials : ITrackerCredentials
{
    private static readonly string Resource = Tsuzuki.Core.AppIdentity.DataFolder + " tracker";
    private readonly PasswordVault vault = new();
    private readonly Dictionary<int, string?> cache = [];

    public string? Read(int trackerId)
    {
        lock (cache)
        {
            if (cache.TryGetValue(trackerId, out var known))
            {
                return known;
            }
            string? secret = null;
            try
            {
                var credential = vault.Retrieve(Resource, trackerId.ToString());
                credential.RetrievePassword();
                secret = credential.Password;
            }
            catch (Exception)
            {
            }
            cache[trackerId] = secret;
            return secret;
        }
    }

    public void Write(int trackerId, string? secret)
    {
        lock (cache)
        {
            try
            {
                vault.Remove(vault.Retrieve(Resource, trackerId.ToString()));
            }
            catch (Exception)
            {
            }
            if (secret is not null)
            {
                vault.Add(new PasswordCredential(Resource, trackerId.ToString(), secret));
            }
            cache[trackerId] = secret;
        }
    }
}
