using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace GestureLink.Windows.Core.Security;

public sealed record ServerIdentity(string ServerId, X509Certificate2 Certificate, string Fingerprint);

public static class ServerIdentityManager
{
    private const string SubjectPrefix = "CN=GestureLink-Server-";

    public static ServerIdentity LoadOrCreate()
    {
        using var store = new X509Store(StoreName.My, StoreLocation.CurrentUser);
        store.Open(OpenFlags.ReadWrite);
        var existing = store.Certificates
            .Find(X509FindType.FindBySubjectName, "GestureLink-Server-", validOnly: false)
            .OfType<X509Certificate2>()
            .Where(certificate => certificate.HasPrivateKey && certificate.NotAfter > DateTimeOffset.UtcNow.AddDays(30))
            .OrderByDescending(certificate => certificate.NotBefore)
            .FirstOrDefault();
        if (existing is not null)
        {
            var id = existing.GetNameInfo(X509NameType.SimpleName, false).Replace("GestureLink-Server-", "", StringComparison.Ordinal);
            return new ServerIdentity(id, existing, Fingerprint(existing));
        }

        var serverId = Guid.NewGuid().ToString("N");
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest($"{SubjectPrefix}{serverId}", key, HashAlgorithmName.SHA256);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature, true));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, false));
        var san = new SubjectAlternativeNameBuilder();
        san.AddDnsName(Environment.MachineName);
        foreach (var ip in LocalPrivateAddresses()) san.AddIpAddress(ip);
        request.CertificateExtensions.Add(san.Build());
        using var temporary = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddYears(5));
        var persistent = X509CertificateLoader.LoadPkcs12(
            temporary.Export(X509ContentType.Pfx),
            password: null,
            X509KeyStorageFlags.PersistKeySet | X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable);
        persistent.FriendlyName = ProductInfo.Name;
        store.Add(persistent);
        return new ServerIdentity(serverId, persistent, Fingerprint(persistent));
    }

    public static IReadOnlyList<IPAddress> LocalPrivateAddresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(adapter => adapter.OperationalStatus == OperationalStatus.Up && adapter.NetworkInterfaceType != NetworkInterfaceType.Loopback)
        .SelectMany(adapter => adapter.GetIPProperties().UnicastAddresses)
        .Select(item => item.Address)
        .Where(IsPrivateOrLocal)
        .Distinct()
        .OrderBy(address => address.AddressFamily == AddressFamily.InterNetwork && !address.IsIPv6LinkLocal &&
            !(address.GetAddressBytes()[0] == 169 && address.GetAddressBytes()[1] == 254) ? 0 : 1)
        .ToArray();

    public static bool IsPrivateOrLocal(IPAddress address)
    {
        if (IPAddress.IsLoopback(address) || address.IsIPv6LinkLocal || address.IsIPv6SiteLocal) return true;
        if (address.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = address.GetAddressBytes();
        return b[0] == 10 || b[0] == 127 || (b[0] == 172 && b[1] is >= 16 and <= 31) || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254);
    }

    private static string Fingerprint(X509Certificate2 certificate) =>
        Convert.ToHexString(SHA256.HashData(certificate.RawData)).ToLowerInvariant();
}
