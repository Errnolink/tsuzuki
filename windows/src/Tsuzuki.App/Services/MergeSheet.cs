using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.App.Navigation;
using Tsuzuki.Core.Library;
using Tsuzuki.Core.Sources;

namespace Tsuzuki.App;

public static class MergeSheet
{
    private const int PerSource = 6;
    private const int LibraryLimit = 8;

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public static async Task ShowAsync(long mangaId, FrameworkElement origin)
    {
        if (Services.Engine is not { } engine || await engine.Library.GetMangaAsync(mangaId) is not { } start)
        {
            return;
        }
        var currentId = mangaId;
        var searchVersion = 0;
        var busy = false;
        CancellationTokenSource? searchCancel = null;
        IReadOnlyList<MergedRef> children = [];
        HashSet<string> members = [];

        var root = new StackPanel { Width = 640 };
        root.Children.Add(new TextBlock { Text = "MERGE · 統合", Style = Res<Style>("TzKicker") });
        var title = new TextBlock { Style = Res<Style>("TzLargeTitle"), Margin = new Thickness(0, 4, 0, 0) };
        root.Children.Add(title);
        var lead = new TextBlock { Style = Res<Style>("TzSecondary"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 8, 0, 0) };
        root.Children.Add(lead);

        var sourcesHeader = new TextBlock { Text = "SOURCES", Style = Res<Style>("TzKicker"), Margin = new Thickness(0, 24, 0, 8) };
        var sources = new StackPanel { Spacing = 6 };
        root.Children.Add(sourcesHeader);
        root.Children.Add(sources);

        root.Children.Add(new TextBlock { Text = "ADD A SOURCE", Style = Res<Style>("TzKicker"), Margin = new Thickness(0, 24, 0, 8) });
        var searchRow = new Grid { ColumnSpacing = 8 };
        searchRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        searchRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        searchRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var query = new TextBox { PlaceholderText = "Search for the same title", Text = start.Title };
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(query, "Search for a title to merge");
        searchRow.Children.Add(query);
        var readable = engine.Sources.All.ToList();
        var scope = new ComboBox { MinWidth = 168 };
        scope.Items.Add("Everywhere");
        scope.Items.Add("Your library");
        foreach (var source in readable)
        {
            scope.Items.Add(engine.Sources.DisplayName(source.Id));
        }
        scope.SelectedIndex = 0;
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(scope, "Where to search");
        Grid.SetColumn(scope, 1);
        searchRow.Children.Add(scope);
        var search = new Button { Style = Res<Style>("TzSoftButton"), Content = "Search" };
        Grid.SetColumn(search, 2);
        searchRow.Children.Add(search);
        root.Children.Add(searchRow);
        var results = new StackPanel { Spacing = 4 };
        root.Children.Add(new ScrollViewer { Content = results, MaxHeight = 300, Margin = new Thickness(0, 10, 0, 0), VerticalScrollBarVisibility = ScrollBarVisibility.Auto });

        var status = new TextBlock { Style = Res<Style>("TzCaption"), VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap };
        var footer = new Grid { Margin = new Thickness(0, 22, 0, 0), ColumnSpacing = 10 };
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footer.Children.Add(status);
        var unmerge = new Button { Style = Res<Style>("TzGhostButton"), Content = "Unmerge", Foreground = Theme.Brush("TzDanger") };
        ToolTipService.SetToolTip(unmerge, "Split this title back into its sources");
        Grid.SetColumn(unmerge, 1);
        footer.Children.Add(unmerge);
        var done = new Button { Style = Res<Style>("TzAccentButton"), Content = "Done", Padding = new Thickness(22, 0, 22, 0) };
        done.Click += (_, _) => _ = App.MainWindow.CloseSheetAsync();
        Grid.SetColumn(done, 2);
        footer.Children.Add(done);
        root.Children.Add(footer);

        void Say(string text, bool error = false)
        {
            status.Text = text;
            status.Foreground = Theme.Brush(error ? "TzDanger" : "TzTextSecondary");
        }

        async Task RenderAsync(bool animate)
        {
            if (await engine.Library.GetMangaAsync(currentId) is not { } manga)
            {
                return;
            }
            children = manga.MergedRefs.Where(r => r.Source != LibraryRefresher.MergedSourceId).ToList();
            members = children.Select(r => Key(r.Source, r.Url)).Append(Key(manga.Source, manga.Url)).ToHashSet();
            var merged = manga.Source == LibraryRefresher.MergedSourceId && children.Count > 0;
            title.Text = merged ? "Merged sources" : "Merge with another title";
            lead.Text = merged
                ? "Chapters from every source show together here and on your phone. Read marks stay with the source each chapter comes from."
                : $"Pick a second source for {manga.Title}. Chapters from both show together, and the merge syncs to your phone.";
            sourcesHeader.Visibility = merged ? Visibility.Visible : Visibility.Collapsed;
            sources.Visibility = sourcesHeader.Visibility;
            unmerge.Visibility = sourcesHeader.Visibility;
            var rows = new List<UIElement>();
            for (var i = 0; i < children.Count; i++)
            {
                rows.Add(await SourceRowAsync(children[i], i));
            }
            sources.Children.Clear();
            foreach (var row in rows)
            {
                sources.Children.Add(row);
            }
            if (animate && rows.Count > 0)
            {
                _ = Motion.Stagger(rows, TimeSpan.FromMilliseconds(30), rise: 8);
            }
        }

        async Task<UIElement> SourceRowAsync(MergedRef child, int index)
        {
            var childId = await engine.Library.FindMangaIdAsync(child.Source, child.Url);
            var details = childId is { } id ? await engine.Library.GetMangaAsync(id) : null;
            var chapterCount = childId is { } owner ? (await engine.Library.GetChaptersAsync(owner)).Count : 0;
            var grid = new Grid { ColumnSpacing = 12 };
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            grid.Children.Add(Cover(details?.ThumbnailUrl, child.Source, 40, 60));
            var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            text.Children.Add(new TextBlock { Text = details?.Title ?? child.Url, Style = Res<Style>("TzBodyStrong"), TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 });
            var caption = $"{engine.Sources.DisplayName(child.Source)} · {chapterCount:N0} {(chapterCount == 1 ? "chapter" : "chapters")}";
            if (!engine.Sources.IsReadable(child.Source))
            {
                caption += " · reads on your phone";
            }
            text.Children.Add(new TextBlock { Text = caption, Style = Res<Style>("TzCaption"), TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 });
            Grid.SetColumn(text, 1);
            grid.Children.Add(text);

            var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
            var info = new RadioButton { Content = "Info", GroupName = "merge-info", IsChecked = child.IsInfoManga, MinWidth = 0, Margin = new Thickness(0, 0, 8, 0) };
            ToolTipService.SetToolTip(info, "Use this source's title, cover and description");
            info.Checked += (_, _) =>
            {
                if (!child.IsInfoManga)
                {
                    Run("merge-info", () => engine.Merges.SetInfoSourceAsync(currentId, child));
                }
            };
            actions.Children.Add(info);
            var up = IconButton(Icons.ChevronUp, "Higher priority", index > 0);
            up.Click += (_, _) => Run("merge-order", () => engine.Merges.ReorderAsync(currentId, Swap(children, index, index - 1)));
            actions.Children.Add(up);
            var down = IconButton(Icons.ChevronDown, "Lower priority", index < children.Count - 1);
            down.Click += (_, _) => Run("merge-order", () => engine.Merges.ReorderAsync(currentId, Swap(children, index, index + 1)));
            actions.Children.Add(down);
            var remove = IconButton(Icons.Close, children.Count > 2 ? "Remove from this merge" : "A merge needs two sources. Use Unmerge instead.", children.Count > 2);
            remove.Click += (_, _) => Run("merge-remove", () => engine.Merges.RemoveAsync(currentId, child));
            actions.Children.Add(remove);
            Grid.SetColumn(actions, 2);
            grid.Children.Add(actions);
            return Card(grid);
        }

        void Run(string where, Func<Task> change)
        {
            if (busy)
            {
                return;
            }
            busy = true;
            _ = Log.Guard(where, async () =>
            {
                try
                {
                    Say(string.Empty);
                    await change();
                }
                catch (InvalidOperationException error)
                {
                    Say(error.Message, error: true);
                }
                finally
                {
                    busy = false;
                    await RenderAsync(animate: false);
                }
            });
        }

        async Task SearchAsync()
        {
            var my = ++searchVersion;
            searchCancel?.Cancel();
            var cts = searchCancel = new CancellationTokenSource();
            var text = query.Text.Trim();
            results.Children.Clear();
            if (text.Length == 0)
            {
                Say("Type a title to search for.");
                return;
            }
            Say("Searching…");
            var found = 0;
            var pick = scope.SelectedIndex;
            if (pick is 0 or 1)
            {
                var library = await engine.Library.GetLibraryAsync();
                foreach (var entry in library
                             .Where(e => e.Source != LibraryRefresher.MergedSourceId && !members.Contains(Key(e.Source, e.Url))
                                 && e.Title.Contains(text, StringComparison.CurrentCultureIgnoreCase))
                             .Take(LibraryLimit))
                {
                    found++;
                    results.Children.Add(ResultRow(entry.Title, entry.ThumbnailUrl, entry.Source, "In your library · " + engine.Sources.DisplayName(entry.Source),
                        () => Task.FromResult(entry.MangaId), refresh: false));
                }
            }
            var targets = pick == 0 ? readable : pick >= 2 ? [readable[pick - 2]] : [];
            var gate = new SemaphoreSlim(4);
            var pending = targets.Select(async source =>
            {
                await gate.WaitAsync(cts.Token);
                try
                {
                    var page = await source.SearchAsync(SearchQuery.ForText(text), 1, cts.Token).WaitAsync(TimeSpan.FromSeconds(20), cts.Token);
                    return (Source: source, Mangas: page.Mangas, Failed: false);
                }
                catch (Exception error) when (error is not OperationCanceledException || !cts.IsCancellationRequested)
                {
                    return (Source: source, Mangas: (IReadOnlyList<SManga>)[], Failed: true);
                }
                finally
                {
                    gate.Release();
                }
            }).ToList();
            var failed = 0;
            try
            {
                await foreach (var done in Task.WhenEach(pending))
                {
                    if (my != searchVersion)
                    {
                        return;
                    }
                    var (source, mangas, sourceFailed) = await done;
                    failed += sourceFailed ? 1 : 0;
                    foreach (var manga in mangas.Where(m => !members.Contains(Key(source.Id, m.Url))).Take(PerSource))
                    {
                        found++;
                        var row = ResultRow(manga.Title, manga.ThumbnailUrl, source.Id, engine.Sources.DisplayName(source.Id),
                            () => engine.Library.UpsertFromSourceAsync(source.Id, manga), refresh: true);
                        results.Children.Add(row);
                        _ = Motion.FadeIn(row, Motion.Short4, rise: 6);
                    }
                }
            }
            catch (OperationCanceledException) when (cts.IsCancellationRequested)
            {
                return;
            }
            if (my != searchVersion)
            {
                return;
            }
            Say(found == 0
                ? failed > 0 ? "Nothing found. Some sources didn't answer." : "Nothing found. Try a shorter or alternative title."
                : $"{found:N0} {(found == 1 ? "match" : "matches")}" + (failed > 0 ? $" · {failed:N0} {(failed == 1 ? "source" : "sources")} didn't answer" : string.Empty));
        }

        UIElement ResultRow(string name, string? cover, long sourceId, string caption, Func<Task<long>> resolve, bool refresh)
        {
            var grid = new Grid { ColumnSpacing = 12 };
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            grid.Children.Add(Cover(cover, sourceId, 32, 48));
            var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            text.Children.Add(new TextBlock { Text = name, Style = Res<Style>("TzBody"), TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 });
            text.Children.Add(new TextBlock { Text = caption, Style = Res<Style>("TzCaption"), TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 });
            Grid.SetColumn(text, 1);
            grid.Children.Add(text);
            var merge = new Button { Style = Res<Style>("TzSoftButton"), Content = "Merge", VerticalAlignment = VerticalAlignment.Center };
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(merge, "Merge " + name);
            merge.Click += (_, _) => Run("merge-add", async () =>
            {
                var candidate = await resolve();
                var parent = await engine.Merges.MergeAsync(currentId, candidate);
                if (refresh)
                {
                    _ = Log.Guard("merge-refresh", async () => await engine.Refresher.RefreshAsync(candidate, includeDetails: true));
                }
                var created = parent != currentId;
                currentId = parent;
                results.Children.Clear();
                Toasts.Show("Merged", $"{name} is now part of this title. It will sync to your phone.", Icons.Merge);
                if (created)
                {
                    await AppNav.OpenDetailsAsync(parent, origin);
                }
            });
            grid.Children.Add(merge);
            Grid.SetColumn(merge, 2);
            return new Border { Padding = new Thickness(8, 6, 8, 6), CornerRadius = Res<CornerRadius>("TzCardRadius"), Child = grid };
        }

        search.Click += (_, _) => _ = Log.Guard("merge-search", SearchAsync);
        query.KeyDown += (_, e) =>
        {
            if (e.Key == Windows.System.VirtualKey.Enter)
            {
                e.Handled = true;
                _ = Log.Guard("merge-search", SearchAsync);
            }
        };
        unmerge.Click += (_, _) => _ = Log.Guard("merge-unmerge", async () =>
        {
            if (!await Dialogs.ConfirmDestructiveAsync("Unmerge this title?",
                    "Each source goes back to your library as its own title, in the same categories. Read progress stays with each source and syncs to your phone.",
                    "Unmerge", "Keep merged"))
            {
                return;
            }
            await engine.Merges.UnmergeAsync(currentId);
            await App.MainWindow.CloseSheetAsync();
            Toasts.Show("Unmerged", "Each source is back in your library.", Icons.Merge);
            await App.MainWindow.Nav.PopAsync();
        });

        void OnClosed(object? sender, EventArgs e)
        {
            searchCancel?.Cancel();
            App.MainWindow.SheetClosed -= OnClosed;
        }
        App.MainWindow.SheetClosed += OnClosed;
        await RenderAsync(animate: false);
        App.MainWindow.ShowSheet(root);
        if (start.Source != LibraryRefresher.MergedSourceId)
        {
            _ = Log.Guard("merge-search", SearchAsync);
        }
    }

    private static string Key(long source, string url) => source + "|" + url;

    private static IReadOnlyList<MergedRef> Swap(IReadOnlyList<MergedRef> children, int from, int to)
    {
        var order = children.ToList();
        (order[from], order[to]) = (order[to], order[from]);
        return order;
    }

    private static Border Card(UIElement child) => new()
    {
        Padding = new Thickness(10, 8, 8, 8),
        CornerRadius = Res<CornerRadius>("TzCardRadius"),
        Background = Theme.Brush("TzCard"),
        BorderBrush = Theme.Brush("TzStroke"),
        BorderThickness = new Thickness(1),
        Child = child,
    };

    private static Border Cover(string? url, long sourceId, double width, double height) => new()
    {
        Width = width,
        Height = height,
        CornerRadius = new CornerRadius(4),
        Background = Theme.Brush("TzCard"),
        Child = new CoverImage { Url = url, SourceId = sourceId, DecodeWidth = (int)(width * 2) },
    };

    private static Button IconButton(string glyph, string label, bool enabled)
    {
        var button = new Button
        {
            Style = Res<Style>("TzIconButton"),
            IsEnabled = enabled,
            Content = new FontIcon { Glyph = glyph, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 15 },
        };
        ToolTipService.SetToolTip(button, label);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(button, label);
        return button;
    }
}
