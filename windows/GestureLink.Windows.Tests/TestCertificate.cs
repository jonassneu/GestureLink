using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace GestureLink.Windows.Tests;

internal static class TestCertificate
{
    public static X509Certificate2 Create()
    {
        var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest("CN=test", key, HashAlgorithmName.SHA256);
        return request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddHours(1));
    }
}

