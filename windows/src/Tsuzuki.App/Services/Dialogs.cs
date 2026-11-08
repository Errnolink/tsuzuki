using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace Tsuzuki.App;

public static class Dialogs
{
    private static readonly SemaphoreSlim Gate = new(1, 1);

    private static async Task<ContentDialogResult> ShowAsync(ContentDialog dialog)
    {
        await Gate.WaitAsync();
        try
        {
            dialog.XamlRoot = App.MainWindow.ContentRoot.XamlRoot;
            dialog.RequestedTheme = ((FrameworkElement)App.MainWindow.ContentRoot).ActualTheme;
            return await dialog.ShowAsync();
        }
        finally
        {
            Gate.Release();
        }
    }

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
        };
        dialog.Opened += (_, _) => box.Focus(FocusState.Programmatic);
        return await ShowAsync(dialog) == ContentDialogResult.Primary ? box.Text : null;
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
        };
        return await ShowAsync(dialog) == ContentDialogResult.Primary;
    }

    public static async Task<bool> ConfirmDestructiveAsync(string title, string body, string confirm, string keep)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = new TextBlock { Text = body, TextWrapping = TextWrapping.Wrap, MaxWidth = 420 },
            PrimaryButtonText = confirm,
            CloseButtonText = keep,
            DefaultButton = ContentDialogButton.Close,
        };
        return await ShowAsync(dialog) == ContentDialogResult.Primary;
    }
}
