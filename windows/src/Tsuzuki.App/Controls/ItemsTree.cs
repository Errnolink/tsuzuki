using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;

namespace Tsuzuki.App.Controls;

internal static class ItemsTree
{
    public static T? FindDescendant<T>(DependencyObject root) where T : DependencyObject
    {
        var count = VisualTreeHelper.GetChildrenCount(root);
        for (var i = 0; i < count; i++)
        {
            var child = VisualTreeHelper.GetChild(root, i);
            if (child is T found)
            {
                return found;
            }
            var nested = FindDescendant<T>(child);
            if (nested is not null)
            {
                return nested;
            }
        }
        return null;
    }

    public static T? FindAncestor<T>(DependencyObject? node) where T : DependencyObject
    {
        while (node is not null)
        {
            if (node is T found)
            {
                return found;
            }
            node = VisualTreeHelper.GetParent(node);
        }
        return null;
    }
}
