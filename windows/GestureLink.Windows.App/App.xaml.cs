using System.Windows;

namespace GestureLink.Windows.App;

public partial class App : System.Windows.Application
{
    private MainViewModel? _viewModel;
    private TrayController? _tray;

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        _viewModel = new MainViewModel();
        await _viewModel.StartAsync();
        var window = new MainWindow(_viewModel);
        MainWindow = window;
        _tray = new TrayController(window, _viewModel, Shutdown);
        window.Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _viewModel?.ReleaseAll();
        _viewModel?.DisposeAsync().AsTask().GetAwaiter().GetResult();
        _tray?.Dispose();
        base.OnExit(e);
    }
}

