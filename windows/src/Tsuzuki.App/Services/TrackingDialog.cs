using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Proto;
using Tsuzuki.Core.Tracking;

namespace Tsuzuki.App;

public static class TrackingDialog
{
    public static async Task ShowAsync(XamlRoot root, long mangaId, string title, double highestRead)
    {
        if (Services.Engine is not { } engine)
        {
            return;
        }
        var body = new StackPanel { Spacing = 12, MinWidth = 460 };
        var dialog = new ContentDialog
        {
            Title = "Tracking",
            Content = new ScrollViewer { Content = body, MaxHeight = 560, Padding = new Thickness(0, 0, 12, 0) },
            CloseButtonText = "Done",
            XamlRoot = root,
        };
        _ = engine.Tracking.RefreshAsync(mangaId).ContinueWith(_ => Ui.Enqueue(root.Content.DispatcherQueue, () => _ = Build()), TaskScheduler.Default);
        await Build();
        await dialog.ShowAsync();

        async Task Build()
        {
            var tracks = await engine.Tracking.GetAsync(mangaId);
            body.Children.Clear();
            foreach (var track in tracks)
            {
                body.Children.Add(Entry(track));
            }
            var unbound = engine.Tracking.Trackers.Where(t => tracks.All(x => x.SyncId != t.Id)).ToList();
            foreach (var tracker in unbound)
            {
                body.Children.Add(SearchPanel(tracker));
            }
            if (tracks.Count == 0 && unbound.All(t => !t.SignedIn))
            {
                body.Children.Add(new TextBlock
                {
                    Text = "Sign in to MangaUpdates or Kitsu in Settings › Tracking to track this title from the PC. Titles tracked on your phone (AniList, MyAnimeList and others) show here after sync, and reading on the PC moves their progress for your phone to send.",
                    TextWrapping = TextWrapping.Wrap,
                    Style = (Style)Application.Current.Resources["TzSecondary"],
                });
            }
        }

        UIElement Entry(BackupTracking track)
        {
            var tracker = engine.Tracking.Tracker(track.SyncId);
            var panel = new StackPanel { Spacing = 6, Padding = new Thickness(14, 12, 14, 12), CornerRadius = new CornerRadius(6), Background = Theme.Brush("TzCardBackground") };
            var header = new Grid();
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var names = new StackPanel();
            names.Children.Add(new TextBlock { Text = TrackerIds.Label(track), Style = (Style)Application.Current.Resources["TzBodyStrong"] });
            names.Children.Add(new TextBlock { Text = track.Title, Style = (Style)Application.Current.Resources["TzCaption"], TextTrimming = TextTrimming.CharacterEllipsis });
            header.Children.Add(names);
            var links = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
            if (Uri.TryCreate(track.TrackingUrl, UriKind.Absolute, out var url))
            {
                links.Children.Add(new HyperlinkButton { Content = "Open", NavigateUri = url });
            }
            var unbind = new Button { Content = "Remove" };
            AutomationProperties.SetName(unbind, $"Stop tracking on {TrackerIds.Label(track)}");
            unbind.Click += (_, _) => _ = Log.Guard("tracking-unbind", async () =>
            {
                await engine.Tracking.UnbindAsync(mangaId, track.SyncId);
                await Build();
            });
            links.Children.Add(unbind);
            Grid.SetColumn(links, 1);
            header.Children.Add(links);
            panel.Children.Add(header);
            if (tracker is null)
            {
                panel.Children.Add(new TextBlock
                {
                    Text = $"Chapter {track.LastChapterRead:0.#}" + (track.TotalChapters > 0 ? $" of {track.TotalChapters}" : string.Empty) + $" · edited on your phone; the PC keeps its progress current for the phone to send.",
                    TextWrapping = TextWrapping.Wrap,
                    Style = (Style)Application.Current.Resources["TzCaption"],
                });
                return panel;
            }
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            var status = new ComboBox { ItemsSource = tracker.Statuses.Select(s => s.Label).ToList(), MinWidth = 150 };
            status.SelectedIndex = Math.Max(0, tracker.Statuses.ToList().FindIndex(s => s.Value == track.Status));
            AutomationProperties.SetName(status, "Status");
            var progress = new NumberBox { Value = track.LastChapterRead, Minimum = 0, SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact, Header = null, Width = 120 };
            AutomationProperties.SetName(progress, "Chapters read");
            var score = new NumberBox { Value = track.Score, Minimum = 0, Maximum = tracker.MaxScore, SmallChange = 0.5, SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact, Width = 100 };
            AutomationProperties.SetName(score, "Score");
            var save = new Button { Content = "Save", Style = (Style)Application.Current.Resources["AccentButtonStyle"], IsEnabled = tracker.SignedIn };
            ToolTipService.SetToolTip(save, tracker.SignedIn ? $"Save to {tracker.Name}" : $"Sign in to {tracker.Name} in Settings to edit");
            save.Click += (_, _) => _ = Log.Guard("tracking-save", async () =>
            {
                var edited = track.Clone();
                edited.Status = tracker.Statuses[Math.Max(0, status.SelectedIndex)].Value;
                edited.LastChapterRead = double.IsNaN(progress.Value) ? track.LastChapterRead : (float)progress.Value;
                edited.Score = double.IsNaN(score.Value) ? 0 : (float)score.Value;
                await engine.Tracking.SaveAsync(mangaId, edited);
                Toasts.Show($"Saved to {tracker.Name}", null, Icons.Check, seconds: 3);
            });
            row.Children.Add(status);
            row.Children.Add(new TextBlock { Text = "Chapter", VerticalAlignment = VerticalAlignment.Center });
            row.Children.Add(progress);
            row.Children.Add(new TextBlock { Text = "Score", VerticalAlignment = VerticalAlignment.Center });
            row.Children.Add(score);
            row.Children.Add(save);
            panel.Children.Add(row);
            return panel;
        }

        UIElement SearchPanel(ITracker tracker)
        {
            var panel = new StackPanel { Spacing = 6 };
            panel.Children.Add(new TextBlock { Text = "Track on " + tracker.Name, Style = (Style)Application.Current.Resources["TzBodyStrong"] });
            if (!tracker.SignedIn)
            {
                panel.Children.Add(new TextBlock { Text = $"Sign in to {tracker.Name} in Settings › Tracking first.", Style = (Style)Application.Current.Resources["TzCaption"] });
                return panel;
            }
            var box = new AutoSuggestBox { Text = title, PlaceholderText = "Search " + tracker.Name, QueryIcon = new SymbolIcon(Symbol.Find) };
            AutomationProperties.SetName(box, "Search " + tracker.Name);
            var results = new StackPanel { Spacing = 4 };
            box.QuerySubmitted += (sender, e) => _ = Log.Guard("tracking-search", async () =>
            {
                results.Children.Clear();
                results.Children.Add(new ProgressRing { IsActive = true, Width = 20, Height = 20, HorizontalAlignment = HorizontalAlignment.Left });
                IReadOnlyList<TrackSearchResult> found;
                try
                {
                    found = await tracker.SearchAsync(e.QueryText);
                }
                catch (HttpRequestException error)
                {
                    results.Children.Clear();
                    results.Children.Add(new TextBlock { Text = "Search failed: " + error.Message, TextWrapping = TextWrapping.Wrap });
                    return;
                }
                results.Children.Clear();
                if (found.Count == 0)
                {
                    results.Children.Add(new TextBlock { Text = "No matches.", Style = (Style)Application.Current.Resources["TzCaption"] });
                }
                foreach (var result in found.Take(8))
                {
                    var pick = new Button { HorizontalAlignment = HorizontalAlignment.Stretch, HorizontalContentAlignment = HorizontalAlignment.Left };
                    var text = new StackPanel();
                    text.Children.Add(new TextBlock { Text = result.Title, TextTrimming = TextTrimming.CharacterEllipsis });
                    if (!string.IsNullOrWhiteSpace(result.Summary))
                    {
                        text.Children.Add(new TextBlock { Text = result.Summary.Replace('\n', ' '), MaxLines = 1, TextTrimming = TextTrimming.CharacterEllipsis, Style = (Style)Application.Current.Resources["TzCaption"] });
                    }
                    pick.Content = text;
                    AutomationProperties.SetName(pick, $"Track as {result.Title}");
                    pick.Click += (_, _) => _ = Log.Guard("tracking-bind", async () =>
                    {
                        pick.IsEnabled = false;
                        try
                        {
                            await engine.Tracking.BindAsync(mangaId, tracker, result, highestRead);
                            Toasts.Show($"Tracking on {tracker.Name}", result.Title, Icons.Check, seconds: 3);
                        }
                        catch (Exception error) when (error is HttpRequestException or TrackerSignInException)
                        {
                            Toasts.Show($"Couldn't track on {tracker.Name}", error.Message, Icons.Alert);
                        }
                        await Build();
                    });
                    results.Children.Add(pick);
                }
            });
            panel.Children.Add(box);
            panel.Children.Add(results);
            return panel;
        }
    }

    public static async Task SignInAsync(XamlRoot root, ITracker tracker)
    {
        var user = new TextBox { Header = tracker.Id == TrackerIds.Kitsu ? "Email" : "Username" };
        var password = new PasswordBox { Header = "Password" };
        var error = new TextBlock { TextWrapping = TextWrapping.Wrap, Foreground = Theme.Brush("TzDanger"), Visibility = Visibility.Collapsed };
        var panel = new StackPanel { Spacing = 10, MinWidth = 340 };
        panel.Children.Add(user);
        panel.Children.Add(password);
        panel.Children.Add(new TextBlock { Text = "Your password goes only to " + tracker.Name + ". Tsuzuki keeps the sign-in token in Windows Credential Manager on this PC; it is never synced.", TextWrapping = TextWrapping.Wrap, Style = (Style)Application.Current.Resources["TzCaption"] });
        panel.Children.Add(error);
        var dialog = new ContentDialog
        {
            Title = "Sign in to " + tracker.Name,
            Content = panel,
            PrimaryButtonText = "Sign in",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = root,
        };
        dialog.PrimaryButtonClick += async (_, e) =>
        {
            var deferral = e.GetDeferral();
            try
            {
                await tracker.LoginAsync(user.Text.Trim(), password.Password);
            }
            catch (Exception failure) when (failure is HttpRequestException or TrackerSignInException or System.Text.Json.JsonException)
            {
                error.Text = failure.Message;
                error.Visibility = Visibility.Visible;
                e.Cancel = true;
            }
            finally
            {
                deferral.Complete();
            }
        };
        await dialog.ShowAsync();
    }
}
