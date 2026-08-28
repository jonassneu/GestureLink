using System.Net;
using GestureLink.Windows.Core.Security;

namespace GestureLink.Windows.Tests;

public sealed class PairingManagerTests
{
    [Fact]
    public void TokenIsSingleUse()
    {
        using var certificate = TestCertificate.Create();
        var identity = new ServerIdentity("test", certificate, "abcd");
        var manager = new PairingManager(identity);
        var offer = manager.CreateOffer("192.168.1.10", 39827);
        Assert.True(manager.Consume(offer.Token));
        Assert.False(manager.Consume(offer.Token));
    }

    [Theory]
    [InlineData("192.168.1.1", true)] [InlineData("10.2.3.4", true)] [InlineData("172.31.5.2", true)]
    [InlineData("8.8.8.8", false)] [InlineData("1.1.1.1", false)]
    public void AddressPolicyAllowsOnlyLocalTargets(string address, bool expected) =>
        Assert.Equal(expected, ServerIdentityManager.IsPrivateOrLocal(IPAddress.Parse(address)));
}

