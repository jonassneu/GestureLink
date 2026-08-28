using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using GestureLink.Windows.Core;
using GestureLink.Windows.Core.Input;
using GestureLink.Windows.Core.Network;
using GestureLink.Windows.Core.Security;
using GestureLink.Windows.Core.Sessions;
using QRCoder;

namespace GestureLink.Windows.App;

public sealed class MainViewModel : INotifyPropertyChanged, IAsyncDisposable
{
    private readonly ServerIdentity _identity = ServerIdentityManager.LoadOrCreate();
    private readonly DeviceStore _devices = new();
    private readonly IInputInjector _input = new Win32InputInjector();
    private readonly PairingManager _pairing;
    private readonly GestureLinkServer _server;
    private readonly DispatcherTimer _pairingRefreshTimer = new() { Interval = TimeSpan.FromSeconds(150) };
    private string _stateLabel = "QR-Code scannen", _deviceName = "—", _lastMessage = "Bereit", _localAddress = "—", _pairingExpiry = "—", _diagnostics = "Listener wird gestartet …";
    private BitmapImage? _pairingQr;

    public MainViewModel()
    {
        _pairing = new PairingManager(_identity);
        _server = new GestureLinkServer(_identity, _pairing, _devices, _input);
        _server.StatusChanged += status => System.Windows.Application.Current.Dispatcher.Invoke(() => ApplyStatus(status));
        _pairingRefreshTimer.Tick += (_, _) => CreatePairingOffer();
        PauseCommand = new RelayCommand(EmergencyStop);
        DisconnectCommand = new RelayCommand(EmergencyStop);
        ReleaseAllCommand = new RelayCommand(ReleaseAll);
        TestInputCommand = new RelayCommand(() => _input.MovePointer(0.002, 0));
        NewPairingCommand = new RelayCommand(CreatePairingOffer);
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    public ObservableCollection<DeviceRecord> Devices { get; } = [];
    public string StateLabel { get => _stateLabel; private set => Set(ref _stateLabel, value); }
    public string DeviceName { get => _deviceName; private set => Set(ref _deviceName, value); }
    public string LastMessage { get => _lastMessage; private set => Set(ref _lastMessage, value); }
    public string LocalAddress { get => _localAddress; private set => Set(ref _localAddress, value); }
    public string PairingExpiry { get => _pairingExpiry; private set => Set(ref _pairingExpiry, value); }
    public string Diagnostics { get => _diagnostics; private set => Set(ref _diagnostics, value); }
    public BitmapImage? PairingQr { get => _pairingQr; private set => Set(ref _pairingQr, value); }
    public bool ExitRequested { get; set; }
    public RelayCommand PauseCommand { get; }
    public RelayCommand DisconnectCommand { get; }
    public RelayCommand ReleaseAllCommand { get; }
    public RelayCommand TestInputCommand { get; }
    public RelayCommand NewPairingCommand { get; }

    public async Task StartAsync()
    {
        await _devices.LoadAsync();
        RefreshDevices();
        await _server.StartAsync();
        CreatePairingOffer();
        _pairingRefreshTimer.Start();
    }

    public void EmergencyStop() { _server.EmergencyStop(); StateLabel = "Pausiert"; }
    public void ReleaseAll() => _input.ReleaseAll();

    private void CreatePairingOffer()
    {
        var overrideHost = Environment.GetEnvironmentVariable("GESTURELINK_PAIR_HOST");
#if USB_TUNNEL
        overrideHost = "127.0.0.1";
#endif
        var address = System.Net.IPAddress.TryParse(overrideHost, out var requested) && ServerIdentityManager.IsPrivateOrLocal(requested)
            ? requested
            : ServerIdentityManager.LocalPrivateAddresses().FirstOrDefault(ip =>
                ip.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork &&
                !(ip.GetAddressBytes()[0] == 169 && ip.GetAddressBytes()[1] == 254));
        if (address is null) { LastMessage = "Keine private IPv4-Adresse gefunden"; return; }
        var offer = _pairing.CreateOffer(address.ToString(), ProductInfo.DefaultPort);
        LocalAddress = offer.Host;
        PairingExpiry = offer.ExpiresAt.ToLocalTime().ToString("HH:mm:ss");
        using var data = QRCodeGenerator.GenerateQrCode(offer.Uri, QRCodeGenerator.ECCLevel.Q);
        var png = new PngByteQRCode(data).GetGraphic(8);
        PairingQr = LoadImage(png);
    }

    private void ApplyStatus(ServerStatus status)
    {
        StateLabel = status.State switch { ControlState.Armed => "Verbunden · aktiv", ControlState.Paused => "Verbunden · pausiert", ControlState.ConnectedNotArmed => "Verbunden · nicht aktiv", ControlState.Faulted => "Sicher gestoppt", _ => "Nicht verbunden" };
        DeviceName = status.DeviceName ?? "—";
        LastMessage = status.LastMessage;
        Diagnostics = $"Status: {status.State}\nSequenz: {status.LastSequence}\nUngültig/verworfen: {status.InvalidMessages}\nLetzte Nachricht: {status.LastMessage}\nNotfall-Hotkey: Ctrl+Alt+Shift+G";
    }

    private void RefreshDevices()
    {
        Devices.Clear();
        foreach (var device in _devices.Snapshot()) Devices.Add(device);
    }

    private static BitmapImage LoadImage(byte[] data)
    {
        using var stream = new MemoryStream(data);
        var image = new BitmapImage();
        image.BeginInit(); image.CacheOption = BitmapCacheOption.OnLoad; image.StreamSource = stream; image.EndInit(); image.Freeze();
        return image;
    }

    private void Set<T>(ref T field, T value, [CallerMemberName] string? property = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return;
        field = value; PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(property));
    }

    public async ValueTask DisposeAsync() { _pairingRefreshTimer.Stop(); ReleaseAll(); await _server.DisposeAsync(); }
}
