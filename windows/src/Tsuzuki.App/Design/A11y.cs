using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;

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
}
