using System.Drawing;
using System.Windows;
using GestureLink.Windows.Core;
using Forms = System.Windows.Forms;

namespace GestureLink.Windows.App;

public sealed class TrayController : IDisposable
{
    private readonly Forms.NotifyIcon _icon;

    public TrayController(Window window, MainViewModel viewModel, Action exit)
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Öffnen", null, (_, _) => { window.Show(); window.Activate(); });
        menu.Items.Add("Steuerung pausieren", null, (_, _) => viewModel.EmergencyStop());
        menu.Items.Add("Alle Eingaben freigeben", null, (_, _) => viewModel.ReleaseAll());
        menu.Items.Add("QR-Code anzeigen", null, (_, _) => { window.Show(); window.Activate(); viewModel.NewPairingCommand.Execute(null); });
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("Beenden", null, (_, _) => { viewModel.ExitRequested = true; viewModel.ReleaseAll(); exit(); });
        _icon = new Forms.NotifyIcon { Text = ProductInfo.Name, Icon = SystemIcons.Application, Visible = true, ContextMenuStrip = menu };
        _icon.DoubleClick += (_, _) => { window.Show(); window.Activate(); };
    }

    public void Dispose() { _icon.Visible = false; _icon.Dispose(); }
}
