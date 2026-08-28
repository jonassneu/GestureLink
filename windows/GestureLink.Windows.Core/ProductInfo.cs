namespace GestureLink.Windows.Core;

public static class ProductInfo
{
    public const string Name = "GestureLink";
    public const int ProtocolVersion = 1;
    public const int DefaultPort = 39827;
    public const int MaxMessageBytes = 65_536;
    public static readonly TimeSpan WatchdogTimeout = TimeSpan.FromMilliseconds(750);
}

