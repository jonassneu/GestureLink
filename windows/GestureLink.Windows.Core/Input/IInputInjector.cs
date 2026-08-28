namespace GestureLink.Windows.Core.Input;

public enum MouseButton { Left, Right, Middle }

public enum SemanticAction
{
    PresentationNext, PresentationPrevious,
    MediaPlayPause, MediaNext, MediaPrevious,
    VolumeUp, VolumeDown, ToggleMute,
    ZoomIn, ZoomOut,
}

public interface IInputInjector
{
    void MovePointer(double normalizedDx, double normalizedDy);
    void LeftClick();
    void RightClick();
    void MiddleClick();
    void SetMouseButton(MouseButton button, bool down);
    void Scroll(double vertical, double horizontal);
    void ExecuteSemanticAction(SemanticAction action);
    void ReleaseAll();
}

public sealed class DryRunInputInjector : IInputInjector
{
    public event Action<string>? Input;
    private void Log(string value) => Input?.Invoke(value);
    public void MovePointer(double normalizedDx, double normalizedDy) => Log($"move {normalizedDx:F4},{normalizedDy:F4}");
    public void LeftClick() => Log("leftClick");
    public void RightClick() => Log("rightClick");
    public void MiddleClick() => Log("middleClick");
    public void SetMouseButton(MouseButton button, bool down) => Log($"{button} {(down ? "down" : "up")}");
    public void Scroll(double vertical, double horizontal) => Log($"scroll {vertical:F2},{horizontal:F2}");
    public void ExecuteSemanticAction(SemanticAction action) => Log(action.ToString());
    public void ReleaseAll() => Log("releaseAll");
}
