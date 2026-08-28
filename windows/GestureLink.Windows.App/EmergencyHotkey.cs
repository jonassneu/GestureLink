using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace GestureLink.Windows.App;

public sealed class EmergencyHotkey : IDisposable
{
    private const int Id = 0x474C;
    private readonly IntPtr _handle;
    private readonly HwndSource _source;
    private readonly Action _action;

    public EmergencyHotkey(Window window, Action action)
    {
        _action = action;
        _handle = new WindowInteropHelper(window).Handle;
        _source = HwndSource.FromHwnd(_handle) ?? throw new InvalidOperationException("No HWND source.");
        _source.AddHook(Hook);
        if (!RegisterHotKey(_handle, Id, 0x0001 | 0x0002 | 0x0004, 0x47))
            throw new Win32Exception(Marshal.GetLastWin32Error(), "Ctrl+Alt+Shift+G konnte nicht registriert werden.");
    }

    private IntPtr Hook(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (message == 0x0312 && wParam.ToInt32() == Id) { _action(); handled = true; }
        return IntPtr.Zero;
    }

    public void Dispose() { UnregisterHotKey(_handle, Id); _source.RemoveHook(Hook); }
    [DllImport("user32.dll", SetLastError = true)] private static extern bool RegisterHotKey(IntPtr window, int id, uint modifiers, uint key);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool UnregisterHotKey(IntPtr window, int id);
}

