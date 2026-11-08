using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Pages;

public static class Onboarding
{
    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public static void Show()
    {
        var root = new StackPanel { Width = 600, Spacing = 0 };
        var mark = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14, Margin = new Thickness(0, 0, 0, 18) };
        mark.Children.Add(new TextBlock { Text = "続き", FontFamily = Res<FontFamily>("TzMinchoFont"), FontSize = 44, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center });
        mark.Children.Add(new Border { Width = 3, Height = 34, CornerRadius = new CornerRadius(1.5), Background = Design.Theme.Brush("TzAccent"), VerticalAlignment = VerticalAlignment.Center });
        mark.Children.Add(new TextBlock { Text = "tsuzuki", FontSize = 15, CharacterSpacing = 240, Foreground = Design.Theme.Brush("TzTextSecondary"), VerticalAlignment = VerticalAlignment.Center });
        root.Children.Add(mark);
        root.Children.Add(new TextBlock { Text = "Pick up where you left off.", Style = Res<Style>("TzLargeTitle"), TextWrapping = TextWrapping.Wrap });
        root.Children.Add(new TextBlock
        {
            Text = "Tsuzuki reads MangaDex on your PC and keeps your phone in step: the same library, the same page. How do you want to start?",
            Style = Res<Style>("TzSecondary"),
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 10, 0, 24),
        });

        var options = new StackPanel { Spacing = 10 };
        options.Children.Add(Option(Icons.Smartphone, "Pair my phone", "Bring your Tsuzuki library over Wi-Fi. Takes a minute.", true, async () =>
        {
            Finish();
            await App.MainWindow.CloseSheetAsync();
            DevicesPanel.Show();
        }));
        options.Children.Add(Option(Icons.Upload, "Import backup", "Restore your manga library from a Mihon or Tachiyomi backup file (.tachibk).", false, async () =>
        {
            Finish();
            await App.MainWindow.CloseSheetAsync();
            await SettingsPage.RestoreAsync();
        }));
        options.Children.Add(Option(Icons.Compass, "Explore MangaDex", "Start fresh and add titles as you find them.", false, async () =>
        {
            Finish();
            await App.MainWindow.CloseSheetAsync();
            await App.MainWindow.NavigateRootAsync("browse");
        }));
        root.Children.Add(options);
        App.MainWindow.ShowSheet(root);
        App.MainWindow.SheetClosed += OnClosed;

        static void OnClosed(object? sender, EventArgs e)
        {
            App.MainWindow.SheetClosed -= OnClosed;
            Finish();
        }
    }

    private static void Finish()
    {
        if (!Services.Settings.OnboardingDone)
        {
            Services.Settings.OnboardingDone = true;
            Services.Settings.Save();
        }
    }

    private static Button Option(string glyph, string title, string body, bool primary, Func<Task> action)
    {
        var grid = new Grid { ColumnSpacing = 16, Padding = new Thickness(16, 14, 16, 14) };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(new Border
        {
            Width = 44,
            Height = 44,
            CornerRadius = new CornerRadius(22),
            Background = Design.Theme.Brush(primary ? "TzAccent" : "TzAccentSoft"),
            Child = new FontIcon { Glyph = glyph, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 19, Foreground = Design.Theme.Brush(primary ? "TzOnAccent" : "TzAccent") },
        });
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 2 };
        text.Children.Add(new TextBlock { Text = title, Style = Res<Style>("TzBodyStrong"), FontSize = 15 });
        text.Children.Add(new TextBlock { Text = body, Style = Res<Style>("TzCaption"), TextWrapping = TextWrapping.Wrap });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);
        var chevron = new FontIcon { Glyph = Icons.ChevronRight, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 16, Foreground = Design.Theme.Brush("TzTextSecondary") };
        Grid.SetColumn(chevron, 2);
        grid.Children.Add(chevron);
        var button = new Button
        {
            Style = Res<Style>("TzRowButton"),
            CornerRadius = Res<CornerRadius>("TzCardRadius"),
            Background = Design.Theme.Brush("TzCard"),
            BorderBrush = Design.Theme.Brush("TzStrokeStrong"),
            BorderThickness = new Thickness(1),
            Content = grid,
        };
        button.Click += (_, _) => _ = Log.Guard("onboarding", action);
        return button;
    }
}
