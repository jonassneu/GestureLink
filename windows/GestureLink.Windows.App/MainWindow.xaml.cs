using System.ComponentModel;
using System.Windows;

namespace GestureLink.Windows.App;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;
    private EmergencyHotkey? _hotkey;

    public MainWindow(MainViewModel viewModel)
    {
        InitializeComponent();
        DataContext = _viewModel = viewModel;
        SourceInitialized += (_, _) => _hotkey = new EmergencyHotkey(this, _viewModel.EmergencyStop);
        Closing += OnClosing;
    }

    private void OnClosing(object? sender, CancelEventArgs e)
    {
        if (!_viewModel.ExitRequested)
        {
            e.Cancel = true;
            Hide();
        }
        else _hotkey?.Dispose();
    }
}

