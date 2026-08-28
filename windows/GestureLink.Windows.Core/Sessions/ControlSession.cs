namespace GestureLink.Windows.Core.Sessions;

public enum ControlState { Disconnected, ConnectedNotArmed, Armed, Paused, Faulted }

public sealed class ControlSession
{
    public ControlState State { get; private set; } = ControlState.Disconnected;
    public string? SessionId { get; private set; }
    public long LastSequence { get; private set; } = -1;

    public void Connected(string sessionId) { Require(ControlState.Disconnected); SessionId = sessionId; State = ControlState.ConnectedNotArmed; LastSequence = -1; }
    public void Arm() { Require(ControlState.ConnectedNotArmed, ControlState.Paused); State = ControlState.Armed; }
    public void Pause() { Require(ControlState.Armed, ControlState.ConnectedNotArmed); State = ControlState.Paused; }
    public void Stop() { if (State != ControlState.Disconnected) State = ControlState.ConnectedNotArmed; }
    public void Fault() => State = ControlState.Faulted;
    public void Disconnect() { State = ControlState.Disconnected; SessionId = null; LastSequence = -1; }
    public bool AcceptSequence(long sequence) { if (sequence <= LastSequence) return false; LastSequence = sequence; return true; }

    private void Require(params ControlState[] allowed)
    {
        if (!allowed.Contains(State)) throw new InvalidOperationException($"Transition not allowed from {State}.");
    }
}

