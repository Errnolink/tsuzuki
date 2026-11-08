using Microsoft.UI.Xaml;

namespace Tsuzuki.App.Navigation;

public interface ICoverHost
{
    IEnumerable<FrameworkElement> CoversOf(long mangaId);
}
