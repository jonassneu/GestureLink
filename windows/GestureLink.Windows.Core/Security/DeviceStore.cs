using System.Security.Cryptography;
using System.IO;
using System.Text.Json;

namespace GestureLink.Windows.Core.Security;

public sealed record DeviceRecord(string ClientId, string Name, string Salt, string Verifier, DateTimeOffset PairedAt, DateTimeOffset LastSeen, bool Revoked);

public sealed class DeviceStore(string? path = null)
{
    private const int Iterations = 210_000;
    private readonly string _path = path ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), ProductInfo.Name, "devices.json");
    private readonly SemaphoreSlim _gate = new(1, 1);
    private List<DeviceRecord> _devices = [];

    public async Task LoadAsync(CancellationToken token = default)
    {
        await _gate.WaitAsync(token).ConfigureAwait(false);
        try
        {
            if (!File.Exists(_path)) return;
            await using var stream = File.OpenRead(_path);
            _devices = await JsonSerializer.DeserializeAsync<List<DeviceRecord>>(stream, cancellationToken: token).ConfigureAwait(false) ?? [];
        }
        finally { _gate.Release(); }
    }

    public IReadOnlyList<DeviceRecord> Snapshot() => _devices.ToArray();

    public async Task AddAsync(string clientId, string name, byte[] credential, CancellationToken token)
    {
        var salt = RandomNumberGenerator.GetBytes(16);
        var verifier = Rfc2898DeriveBytes.Pbkdf2(credential, salt, Iterations, HashAlgorithmName.SHA256, 32);
        var now = DateTimeOffset.UtcNow;
        _devices.RemoveAll(device => device.ClientId == clientId);
        _devices.Add(new DeviceRecord(clientId, name, Convert.ToBase64String(salt), Convert.ToBase64String(verifier), now, now, false));
        await SaveAsync(token).ConfigureAwait(false);
    }

    public async Task<bool> AuthenticateAsync(string clientId, byte[] credential, CancellationToken token)
    {
        var device = _devices.SingleOrDefault(item => item.ClientId == clientId && !item.Revoked);
        if (device is null) return false;
        var verifier = Rfc2898DeriveBytes.Pbkdf2(credential, Convert.FromBase64String(device.Salt), Iterations, HashAlgorithmName.SHA256, 32);
        var valid = CryptographicOperations.FixedTimeEquals(verifier, Convert.FromBase64String(device.Verifier));
        if (valid)
        {
            _devices[_devices.IndexOf(device)] = device with { LastSeen = DateTimeOffset.UtcNow };
            await SaveAsync(token).ConfigureAwait(false);
        }
        return valid;
    }

    public async Task RevokeAsync(string clientId, CancellationToken token = default)
    {
        var index = _devices.FindIndex(item => item.ClientId == clientId);
        if (index >= 0) _devices[index] = _devices[index] with { Revoked = true };
        await SaveAsync(token).ConfigureAwait(false);
    }

    private async Task SaveAsync(CancellationToken token)
    {
        await _gate.WaitAsync(token).ConfigureAwait(false);
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
            var temporary = _path + ".tmp";
            await using (var stream = File.Create(temporary))
                await JsonSerializer.SerializeAsync(stream, _devices, cancellationToken: token).ConfigureAwait(false);
            File.Move(temporary, _path, true);
        }
        finally { _gate.Release(); }
    }
}
