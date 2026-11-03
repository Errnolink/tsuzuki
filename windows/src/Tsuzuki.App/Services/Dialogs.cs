using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace Tsuzuki.App;

public static class Dialogs
{
    public static async Task<string?> PromptAsync(string title, string placeholder, string confirm)
    {
        var box = new TextBox { PlaceholderText = placeholder, MinWidth = 320 };
        var dialog = new ContentDialog
        {
            Title = title,
            Content = box,
            PrimaryButtonText = confirm,
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = App.MainWindow.ContentRoot.XamlRoot,
            RequestedTheme = ((FrameworkElement)App.MainWindow.ContentRoot).ActualTheme,
        };
        dialog.Opened += (_, _) => box.Focus(FocusState.Programmatic);
        return await dialog.ShowAsync() == ContentDialogResult.Primary ? box.Text : null;
    }

    public static async Task<bool> ConfirmAsync(string title, string body, string confirm)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = new TextBlock { Text = body, TextWrapping = TextWrapping.Wrap, MaxWidth = 420 },
            PrimaryButtonText = confirm,
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = App.MainWindow.ContentRoot.XamlRoot,
            RequestedTheme = ((FrameworkElement)App.MainWindow.ContentRoot).ActualTheme,
        };
        return await dialog.ShowAsync() == ContentDialogResult.Primary;
    }
}
