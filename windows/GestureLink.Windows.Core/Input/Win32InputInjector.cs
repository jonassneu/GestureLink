using System.ComponentModel;
using System.Runtime.InteropServices;

namespace GestureLink.Windows.Core.Input;

public sealed class Win32InputInjector(double pointerScale = 1.0, double scrollScale = 1.0) : IInputInjector
{
    private readonly HashSet<MouseButton> _down = [];
    private double _wheelRemainder;
    private double _horizontalRemainder;

    public void MovePointer(double normalizedDx, double normalizedDy)
    {
        EnsureFinite(normalizedDx, normalizedDy);
        normalizedDx = Math.Clamp(normalizedDx, -0.25, 0.25);
        normalizedDy = Math.Clamp(normalizedDy, -0.25, 0.25);
        if (!Native.GetCursorPos(out var point)) throw new Win32Exception(Marshal.GetLastWin32Error());
        var left = Native.GetSystemMetrics(Native.SM_XVIRTUALSCREEN);
        var top = Native.GetSystemMetrics(Native.SM_YVIRTUALSCREEN);
        var width = Math.Max(1, Native.GetSystemMetrics(Native.SM_CXVIRTUALSCREEN));
        var height = Math.Max(1, Native.GetSystemMetrics(Native.SM_CYVIRTUALSCREEN));
        var x = Math.Clamp(point.X + (int)Math.Round(normalizedDx * width * pointerScale), left, left + width - 1);
        var y = Math.Clamp(point.Y + (int)Math.Round(normalizedDy * height * pointerScale), top, top + height - 1);
        if (!Native.SetCursorPos(x, y)) throw new Win32Exception(Marshal.GetLastWin32Error());
    }

    public void LeftClick() => Click(MouseButton.Left);
    public void RightClick() => Click(MouseButton.Right);
    public void MiddleClick() => Click(MouseButton.Middle);

    public void SetMouseButton(MouseButton button, bool down)
    {
        if (down && !_down.Add(button)) return;
        if (!down) _down.Remove(button);
        SendMouse(ButtonFlag(button, down), 0);
    }

    public void Scroll(double vertical, double horizontal)
    {
        EnsureFinite(vertical, horizontal);
        _wheelRemainder += Math.Clamp(vertical, -20, 20) * scrollScale * Native.WHEEL_DELTA;
        _horizontalRemainder += Math.Clamp(horizontal, -20, 20) * scrollScale * Native.WHEEL_DELTA;
        FlushWheel(ref _wheelRemainder, Native.MOUSEEVENTF_WHEEL);
        FlushWheel(ref _horizontalRemainder, Native.MOUSEEVENTF_HWHEEL);
    }

    public void ExecuteSemanticAction(SemanticAction action)
    {
        switch (action)
        {
            case SemanticAction.PresentationNext: KeyStroke(Native.VK_RIGHT); break;
            case SemanticAction.PresentationPrevious: KeyStroke(Native.VK_LEFT); break;
            case SemanticAction.MediaPlayPause: KeyStroke(Native.VK_MEDIA_PLAY_PAUSE); break;
            case SemanticAction.MediaNext: KeyStroke(Native.VK_MEDIA_NEXT_TRACK); break;
            case SemanticAction.MediaPrevious: KeyStroke(Native.VK_MEDIA_PREV_TRACK); break;
            case SemanticAction.VolumeUp: KeyStroke(Native.VK_VOLUME_UP); break;
            case SemanticAction.VolumeDown: KeyStroke(Native.VK_VOLUME_DOWN); break;
            case SemanticAction.ToggleMute: KeyStroke(Native.VK_VOLUME_MUTE); break;
            case SemanticAction.ZoomIn: CtrlWheel(Native.WHEEL_DELTA); break;
            case SemanticAction.ZoomOut: CtrlWheel(-Native.WHEEL_DELTA); break;
            default: throw new ArgumentOutOfRangeException(nameof(action));
        }
    }

    public void ReleaseAll()
    {
        foreach (var button in Enum.GetValues<MouseButton>()) SendMouse(ButtonFlag(button, false), 0);
        _down.Clear();
        foreach (var key in new ushort[] { Native.VK_CONTROL, Native.VK_SHIFT, Native.VK_MENU }) SendKey(key, true);
    }

    private void Click(MouseButton button)
    {
        SetMouseButton(button, true);
        SetMouseButton(button, false);
    }

    private void FlushWheel(ref double accumulator, uint flag)
    {
        var amount = (int)(accumulator / Native.WHEEL_DELTA) * Native.WHEEL_DELTA;
        if (amount == 0) return;
        accumulator -= amount;
        SendMouse(flag, amount);
    }

    private static void CtrlWheel(int delta)
    {
        SendKey(Native.VK_CONTROL, false);
        try { SendMouse(Native.MOUSEEVENTF_WHEEL, delta); }
        finally { SendKey(Native.VK_CONTROL, true); }
    }

    private static void KeyStroke(ushort key)
    {
        SendKey(key, false);
        SendKey(key, true);
    }

    private static uint ButtonFlag(MouseButton button, bool down) => (button, down) switch
    {
        (MouseButton.Left, true) => Native.MOUSEEVENTF_LEFTDOWN,
        (MouseButton.Left, false) => Native.MOUSEEVENTF_LEFTUP,
        (MouseButton.Right, true) => Native.MOUSEEVENTF_RIGHTDOWN,
        (MouseButton.Right, false) => Native.MOUSEEVENTF_RIGHTUP,
        (MouseButton.Middle, true) => Native.MOUSEEVENTF_MIDDLEDOWN,
        (MouseButton.Middle, false) => Native.MOUSEEVENTF_MIDDLEUP,
        _ => throw new ArgumentOutOfRangeException(nameof(button)),
    };

    private static void SendMouse(uint flags, int data)
    {
        var input = Native.INPUT.Mouse(flags, data);
        if (Native.SendInput(1, [input], Marshal.SizeOf<Native.INPUT>()) != 1) throw new Win32Exception(Marshal.GetLastWin32Error());
    }

    private static void SendKey(ushort key, bool up)
    {
        var input = Native.INPUT.Key(key, up);
        if (Native.SendInput(1, [input], Marshal.SizeOf<Native.INPUT>()) != 1) throw new Win32Exception(Marshal.GetLastWin32Error());
    }

    private static void EnsureFinite(params double[] values)
    {
        if (values.Any(value => !double.IsFinite(value))) throw new ArgumentOutOfRangeException(nameof(values));
    }

    private static class Native
    {
        public const int SM_XVIRTUALSCREEN = 76, SM_YVIRTUALSCREEN = 77, SM_CXVIRTUALSCREEN = 78, SM_CYVIRTUALSCREEN = 79;
        public const int WHEEL_DELTA = 120;
        public const uint INPUT_MOUSE = 0, INPUT_KEYBOARD = 1;
        public const uint MOUSEEVENTF_LEFTDOWN = 0x0002, MOUSEEVENTF_LEFTUP = 0x0004, MOUSEEVENTF_RIGHTDOWN = 0x0008, MOUSEEVENTF_RIGHTUP = 0x0010;
        public const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020, MOUSEEVENTF_MIDDLEUP = 0x0040, MOUSEEVENTF_WHEEL = 0x0800, MOUSEEVENTF_HWHEEL = 0x01000;
        public const uint KEYEVENTF_KEYUP = 0x0002;
        public const ushort VK_CONTROL = 0x11, VK_SHIFT = 0x10, VK_MENU = 0x12, VK_LEFT = 0x25, VK_RIGHT = 0x27;
        public const ushort VK_VOLUME_MUTE = 0xAD, VK_VOLUME_DOWN = 0xAE, VK_VOLUME_UP = 0xAF, VK_MEDIA_NEXT_TRACK = 0xB0, VK_MEDIA_PREV_TRACK = 0xB1, VK_MEDIA_PLAY_PAUSE = 0xB3;

        [DllImport("user32.dll", SetLastError = true)] public static extern bool GetCursorPos(out POINT point);
        [DllImport("user32.dll", SetLastError = true)] public static extern bool SetCursorPos(int x, int y);
        [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
        [DllImport("user32.dll", SetLastError = true)] public static extern uint SendInput(uint count, INPUT[] inputs, int size);

        [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
        [StructLayout(LayoutKind.Sequential)] public struct INPUT
        {
            public uint Type;
            public UNION Data;
            public static INPUT Mouse(uint flags, int data) => new() { Type = INPUT_MOUSE, Data = new UNION { Mouse = new MOUSEINPUT { Flags = flags, MouseData = data } } };
            public static INPUT Key(ushort key, bool up) => new() { Type = INPUT_KEYBOARD, Data = new UNION { Keyboard = new KEYBDINPUT { VirtualKey = key, Flags = up ? KEYEVENTF_KEYUP : 0 } } };
        }
        [StructLayout(LayoutKind.Explicit)] public struct UNION { [FieldOffset(0)] public MOUSEINPUT Mouse; [FieldOffset(0)] public KEYBDINPUT Keyboard; }
        [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int Dx, Dy, MouseData; public uint Flags, Time; public UIntPtr ExtraInfo; }
        [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort VirtualKey, ScanCode; public uint Flags, Time; public UIntPtr ExtraInfo; }
    }
}
