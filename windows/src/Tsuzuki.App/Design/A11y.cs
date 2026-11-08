using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;

namespace Tsuzuki.App.Design;

public static class A11y
{
    public static void NameFromTooltips(params FrameworkElement[] elements)
    {
        foreach (var element in elements)
        {
            if (ToolTipService.GetToolTip(element) is string tip && string.IsNullOrEmpty(AutomationProperties.GetName(element)))
            {
                AutomationProperties.SetName(element, tip);
            }
        }
    }

    public static void Name(DependencyObject element, string name) => AutomationProperties.SetName(element, name);

    public static void NameToggle(ToggleButton toggle, string label) =>
        AutomationProperties.SetName(toggle, label + (toggle.IsChecked == true ? ", on" : ", off"));
}
