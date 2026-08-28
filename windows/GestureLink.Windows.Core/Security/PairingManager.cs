using System.Security.Cryptography;
using System.Net;

namespace GestureLink.Windows.Core.Security;

public sealed record PairingOffer(string Uri, string Token, DateTimeOffset ExpiresAt, string Host, int Port);

public sealed class PairingManager(ServerIdentity identity, TimeProvider? timeProvider = null)
{
    private readonly TimeProvider _time = timeProvider ?? TimeProvider.System;
    private byte[]? _token;
    private DateTimeOffset _expiresAt;

    public PairingOffer CreateOffer(string host, int port, TimeSpan? lifetime = null)
    {
        if (!IPAddress.TryParse(host, out var address) || !ServerIdentityManager.IsPrivateOrLocal(address))
            throw new ArgumentException("Pairing requires a private or local IP address.", nameof(host));
        _token = RandomNumberGenerator.GetBytes(32);
        _expiresAt = _time.GetUtcNow().Add(lifetime ?? TimeSpan.FromMinutes(3));
        var token = Base64Url(_token);
        var uri = $"gesturelink://pair?v=1&host={Uri.EscapeDataString(host)}&port={port}&serverId={identity.ServerId}&fingerprint={identity.Fingerprint}&token={Uri.EscapeDataString(token)}";
        return new PairingOffer(uri, token, _expiresAt, host, port);
    }

    public bool Consume(string supplied)
    {
        var token = _token;
        if (token is null || _time.GetUtcNow() > _expiresAt) return false;
        byte[] candidate;
        try { candidate = FromBase64Url(supplied); }
        catch (FormatException) { return false; }
        if (!CryptographicOperations.FixedTimeEquals(token, candidate)) return false;
        CryptographicOperations.ZeroMemory(token);
        _token = null;
        return true;
    }

    private static string Base64Url(byte[] value) => Convert.ToBase64String(value).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    private static byte[] FromBase64Url(string value)
    {
        var normal = value.Replace('-', '+').Replace('_', '/');
        normal = normal.PadRight(normal.Length + ((4 - normal.Length % 4) % 4), '=');
        return Convert.FromBase64String(normal);
    }
}
