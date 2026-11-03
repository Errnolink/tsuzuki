using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Animation;

namespace Tsuzuki.App.Design;

public static class ListMotion
{
    public static TransitionCollection ItemTransitions() => Services.Settings.LabLists
        ? new TransitionCollection { new AddDeleteThemeTransition(), new RepositionThemeTransition() }
        : new TransitionCollection();
}
