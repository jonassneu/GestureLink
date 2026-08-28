using GestureLink.Windows.Core.Sessions;

namespace GestureLink.Windows.Tests;

public sealed class ControlSessionTests
{
    [Fact]
    public void ReconnectNeverArmsAutomatically()
    {
        var session = new ControlSession();
        session.Connected("one"); session.Arm(); session.Disconnect(); session.Connected("two");
        Assert.Equal(ControlState.ConnectedNotArmed, session.State);
    }

    [Fact]
    public void SequenceMustIncrease()
    {
        var session = new ControlSession(); session.Connected("test");
        Assert.True(session.AcceptSequence(1));
        Assert.False(session.AcceptSequence(1));
        Assert.False(session.AcceptSequence(0));
        Assert.True(session.AcceptSequence(2));
    }
}

