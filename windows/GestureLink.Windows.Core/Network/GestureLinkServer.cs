using System.Net;
using System.IO;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text.Json;
using GestureLink.Windows.Core.Input;
using GestureLink.Windows.Core.Protocol;
using GestureLink.Windows.Core.Security;
using GestureLink.Windows.Core.Sessions;

namespace GestureLink.Windows.Core.Network;

public sealed record ServerStatus(ControlState State, string? DeviceName, string LastMessage, long LastSequence, long InvalidMessages);

public sealed class GestureLinkServer(ServerIdentity identity, PairingManager pairing, DeviceStore devices, IInputInjector input, int port = ProductInfo.DefaultPort) : IAsyncDisposable
{
    private readonly TcpListener _listener = new(IPAddress.Any, port);
    private readonly SemaphoreSlim _activeController = new(1, 1);
    private CancellationTokenSource? _lifetime;
    private Task? _acceptLoop;
    private long _invalid;
    public event Action<ServerStatus>? StatusChanged;

    public Task StartAsync(CancellationToken token = default)
    {
        if (_lifetime is not null) return Task.CompletedTask;
        _lifetime = CancellationTokenSource.CreateLinkedTokenSource(token);
        _listener.Start(4);
        _acceptLoop = AcceptLoopAsync(_lifetime.Token);
        Publish(ControlState.Disconnected, null, "Listener gestartet", -1);
        return Task.CompletedTask;
    }

    public async Task StopAsync()
    {
        if (_lifetime is null) return;
        _lifetime.Cancel();
        _listener.Stop();
        input.ReleaseAll();
        if (_acceptLoop is not null) await _acceptLoop.ConfigureAwait(false);
        _lifetime.Dispose();
        _lifetime = null;
    }

    public void EmergencyStop()
    {
        input.ReleaseAll();
        Publish(ControlState.Paused, null, "Notfallstopp", -1);
    }

    private async Task AcceptLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var client = await _listener.AcceptTcpClientAsync(token).ConfigureAwait(false);
                _ = HandleClientAsync(client, token);
            }
            catch (OperationCanceledException) when (token.IsCancellationRequested) { break; }
            catch (ObjectDisposedException) when (token.IsCancellationRequested) { break; }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken serverToken)
    {
        using (client)
        await using (var tls = new SslStream(client.GetStream(), false))
        {
            var session = new ControlSession();
            var ownsController = false;
            string? deviceName = null;
            long outgoingSequence = 0;
            var sessionId = Guid.NewGuid().ToString("N");
            try
            {
                await tls.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = identity.Certificate,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                    ClientCertificateRequired = false,
                }, serverToken).ConfigureAwait(false);
                session.Connected(sessionId);
                Publish(session.State, null, "TLS verbunden", -1);

                var authenticated = false;
                while (!serverToken.IsCancellationRequested)
                {
                    using var watchdog = CancellationTokenSource.CreateLinkedTokenSource(serverToken);
                    watchdog.CancelAfter(ProductInfo.WatchdogTimeout);
                    Envelope? message;
                    try { message = await MessageFraming.ReadAsync(tls, watchdog.Token).ConfigureAwait(false); }
                    catch (OperationCanceledException) when (!serverToken.IsCancellationRequested)
                    {
                        throw new TimeoutException("Heartbeat-Watchdog abgelaufen.");
                    }
                    if (message is null) break;
                    if (!session.AcceptSequence(message.Sequence)) throw new ProtocolException("Sequence is not increasing.");
                    Publish(session.State, deviceName, message.Type, message.Sequence);

                    if (message.Type == "clientHello")
                    {
                        var hello = message.PayloadAs<ClientHelloPayload>();
                        deviceName = hello.DeviceName;
                        await SendAsync(tls, "serverHello", sessionId, outgoingSequence++, new { serverId = identity.ServerId, serverName = Environment.MachineName, version = 1 }, serverToken);
                        continue;
                    }
                    if (message.Type == "pairRequest")
                    {
                        var request = message.PayloadAs<PairRequestPayload>();
                        if (!pairing.Consume(request.Token)) { await SendAsync(tls, "pairRejected", sessionId, outgoingSequence++, new { reason = "invalidOrExpiredToken" }, serverToken); break; }
                        var credential = RandomNumberGenerator.GetBytes(32);
                        await devices.AddAsync(request.ClientId, request.DeviceName, credential, serverToken).ConfigureAwait(false);
                        await SendAsync(tls, "pairAccepted", sessionId, outgoingSequence++, new { clientId = request.ClientId, credential = Convert.ToBase64String(credential) }, serverToken);
                        CryptographicOperations.ZeroMemory(credential);
                        continue;
                    }
                    if (message.Type == "authenticate")
                    {
                        var request = message.PayloadAs<AuthenticatePayload>();
                        byte[] credential;
                        try { credential = Convert.FromBase64String(request.Credential); }
                        catch (FormatException) { throw new ProtocolException("Invalid credential encoding."); }
                        authenticated = await devices.AuthenticateAsync(request.ClientId, credential, serverToken).ConfigureAwait(false);
                        CryptographicOperations.ZeroMemory(credential);
                        if (!authenticated || !await _activeController.WaitAsync(0, serverToken).ConfigureAwait(false))
                        {
                            await SendAsync(tls, "error", sessionId, outgoingSequence++, new { code = authenticated ? "controllerBusy" : "authenticationFailed" }, serverToken);
                            break;
                        }
                        ownsController = true;
                        await SendAsync(tls, "authenticated", sessionId, outgoingSequence++, new { state = "connectedNotArmed" }, serverToken);
                        continue;
                    }
                    if (!authenticated) throw new ProtocolException("Command before authentication.");
                    await DispatchAsync(message, session, serverToken).ConfigureAwait(false);
                }
            }
            catch (Exception error) when (error is IOException or SocketException or AuthenticationException or ProtocolException or TimeoutException or OperationCanceledException)
            {
                Interlocked.Increment(ref _invalid);
                session.Fault();
                Publish(session.State, deviceName, Sanitize(error), session.LastSequence);
            }
            finally
            {
                input.ReleaseAll();
                if (ownsController) _activeController.Release();
                session.Disconnect();
                Publish(session.State, deviceName, "Verbindung beendet; alle Eingaben freigegeben", session.LastSequence);
            }
        }
    }

    private Task DispatchAsync(Envelope message, ControlSession session, CancellationToken token)
    {
        switch (message.Type)
        {
            case "startControl": if (session.State != ControlState.Armed) session.Arm(); break;
            case "stopControl": input.ReleaseAll(); session.Stop(); break;
            case "pauseControl": input.ReleaseAll(); session.Pause(); break;
            case "resumeControl": if (session.State != ControlState.Armed) session.Arm(); break;
            case "releaseAll": input.ReleaseAll(); break;
            case "heartbeat": case "ping": case "pong": case "gestureState": break;
            default:
                if (session.State != ControlState.Armed) throw new ProtocolException("Input command while session is not armed.");
                DispatchInput(message);
                break;
        }
        return Task.CompletedTask;
    }

    private void DispatchInput(Envelope message)
    {
        switch (message.Type)
        {
            case "pointerDelta":
                var pointer = message.PayloadAs<PointerDeltaPayload>();
                EnsureFinite(pointer.Dx, pointer.Dy, pointer.Confidence);
                if (pointer.Confidence >= 0.6) input.MovePointer(Math.Clamp(pointer.Dx, -0.25, 0.25), Math.Clamp(pointer.Dy, -0.25, 0.25));
                break;
            case "mouseClick":
                var click = ParseButton(message.PayloadAs<MouseClickPayload>().Button);
                if (click == MouseButton.Left) input.LeftClick(); else if (click == MouseButton.Right) input.RightClick(); else input.MiddleClick();
                break;
            case "mouseButton":
                var button = message.PayloadAs<MouseButtonPayload>();
                input.SetMouseButton(ParseButton(button.Button), button.Down);
                break;
            case "scroll":
                var scroll = message.PayloadAs<ScrollPayload>();
                EnsureFinite(scroll.Vertical, scroll.Horizontal);
                input.Scroll(Math.Clamp(scroll.Vertical, -20, 20), Math.Clamp(scroll.Horizontal, -20, 20));
                break;
            case "semanticAction": input.ExecuteSemanticAction(ParseAction(message.PayloadAs<SemanticActionPayload>().Action)); break;
            default: throw new ProtocolException("Unknown input message.");
        }
    }

    private static MouseButton ParseButton(string value) => value switch { "left" => MouseButton.Left, "right" => MouseButton.Right, "middle" => MouseButton.Middle, _ => throw new ProtocolException("Unknown mouse button.") };
    private static SemanticAction ParseAction(string value) => value switch
    {
        "presentationNext" => SemanticAction.PresentationNext, "presentationPrevious" => SemanticAction.PresentationPrevious,
        "mediaPlayPause" => SemanticAction.MediaPlayPause, "mediaNext" => SemanticAction.MediaNext, "mediaPrevious" => SemanticAction.MediaPrevious,
        "volumeUp" => SemanticAction.VolumeUp, "volumeDown" => SemanticAction.VolumeDown, "toggleMute" => SemanticAction.ToggleMute,
        "zoomIn" => SemanticAction.ZoomIn, "zoomOut" => SemanticAction.ZoomOut,
        _ => throw new ProtocolException("Semantic action is not allowlisted."),
    };

    private static void EnsureFinite(params double[] values) { if (values.Any(value => !double.IsFinite(value))) throw new ProtocolException("Non-finite number."); }
    private static string Sanitize(Exception error) => error is OperationCanceledException ? "Abgebrochen" : error.Message.Replace('\r', ' ').Replace('\n', ' ');
    private void Publish(ControlState state, string? device, string message, long sequence) => StatusChanged?.Invoke(new(state, device, message, sequence, Interlocked.Read(ref _invalid)));
    private static ValueTask SendAsync<T>(Stream stream, string type, string sessionId, long sequence, T payload, CancellationToken token) => MessageFraming.WriteAsync(stream, ProtocolJson.Create(type, sessionId, sequence, payload), token);

    public async ValueTask DisposeAsync() { await StopAsync().ConfigureAwait(false); identity.Certificate.Dispose(); _activeController.Dispose(); }
}
