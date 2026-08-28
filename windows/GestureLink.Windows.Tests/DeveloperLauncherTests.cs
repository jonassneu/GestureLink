using System.Threading;
using System.Windows;
using GestureLink.Windows.App;

namespace GestureLink.Windows.Tests;

public sealed class DeveloperLauncherTests
{
    [Fact]
    public void RunInteractiveAppOnlyWhenExplicitlyRequested()
    {
        if (Environment.GetEnvironmentVariable("GESTURELINK_RUN_APP") != "1") return;

        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                var application = new System.Windows.Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
                var viewModel = new MainViewModel { ExitRequested = true };
                var window = new MainWindow(viewModel);
                window.Loaded += async (_, _) => await viewModel.StartAsync();
                application.Run(window);
                viewModel.DisposeAsync().AsTask().GetAwaiter().GetResult();
            }
            catch (Exception error) { failure = error; }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null) throw failure;
    }
}
