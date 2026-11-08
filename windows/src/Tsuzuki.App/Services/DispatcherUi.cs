using Microsoft.UI.Dispatching;
using Tsuzuki.Presentation;

namespace Tsuzuki.App;

public sealed class DispatcherUi(DispatcherQueue queue) : IUiThread
{
    public void Post(Action work) => Ui.Enqueue(queue, work, "view-model");
}
