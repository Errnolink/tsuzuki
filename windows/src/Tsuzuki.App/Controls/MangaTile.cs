using Microsoft.UI.Xaml.Automation;
using System.Numerics;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Design;

namespace Tsuzuki.App.Controls;

public delegate void TileAction(MangaTile tile);

public sealed record TileData(long MangaId, string Title, string? ThumbnailUrl, string Caption, int Badge, bool PhoneOnly, bool Merged, object? Tag = null, bool InLibrary = false, TileAction? AddAction = null, long Source = 0);

public sealed partial class MangaTile : Button
{
    private static Style? tileStyle;
    private static Style? addButtonStyle;
    private readonly Border coverFrame;
    private readonly CoverImage cover;
    private readonly Border badge;
    private readonly TextBlock badgeText;
    private readonly StackPanel marks;
    private readonly TextBlock title;
    private readonly TextBlock caption;
    private readonly Grid coverGrid;
    private Button? add;
    private FontIcon? addIcon;
    private MenuFlyout? addMenu;
    private bool addShown;
    private string marksShown = "|";

    public MangaTile()
    {
        Style = tileStyle ??= (Style)Microsoft.UI.Xaml.Markup.XamlReader.Load(
            """
            <Style xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" TargetType="Button">
              <Setter Property="Background" Value="Transparent" />
              <Setter Property="Padding" Value="0" />
              <Setter Property="UseSystemFocusVisuals" Value="True" />
              <Setter Property="FocusVisualMargin" Value="-4" />
              <Setter Property="HorizontalAlignment" Value="Stretch" />
              <Setter Property="HorizontalContentAlignment" Value="Stretch" />
              <Setter Property="VerticalContentAlignment" Value="Top" />
              <Setter Property="Template">
                <Setter.Value>
                  <ControlTemplate TargetType="Button">
                    <ContentPresenter Content="{TemplateBinding Content}" HorizontalContentAlignment="Stretch" VerticalContentAlignment="Top" />
                  </ControlTemplate>
                </Setter.Value>
              </Setter>
            </Style>
            """);

        cover = new CoverImage { DecodeWidth = 320 };
        var aspect = new AspectBox { Ratio = 1.5 };
        aspect.Children.Add(cover);

        badgeText = new TextBlock { FontSize = 11, FontWeight = FontWeights.Bold, Foreground = Design.Theme.Brush("TzOnAccent"), TextLineBounds = TextLineBounds.Tight, VerticalAlignment = VerticalAlignment.Center };
        badge = new Border
        {
            Background = Design.Theme.Brush("TzAccent"),
            BorderBrush = Design.Theme.Brush("TzBackground"),
            BorderThickness = new Thickness(1.5),
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(6, 1, 6, 1),
            MinWidth = 24,
            Height = 20,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 8, 8, 0),
            Child = badgeText,
        };
        marks = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 4,
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(8),
        };

        coverGrid = new Grid();
        coverGrid.Children.Add(aspect);
        coverGrid.Children.Add(badge);
        coverGrid.Children.Add(marks);

        coverFrame = new Border
        {
            CornerRadius = new CornerRadius(10),
            BorderBrush = Design.Theme.Brush("TzStrokeStrong"),
            BorderThickness = new Thickness(1),
            Child = coverGrid,
            TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromMilliseconds(220) },
            ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromMilliseconds(220) },
        };

        title = new TextBlock
        {
            Style = Res<Style>("TzBodyStrong"),
            FontSize = 13,
            LineHeight = 17,
            Height = 36,
            TextWrapping = TextWrapping.Wrap,
            MaxLines = 2,
            Margin = new Thickness(2, 10, 2, 0),
        };
        caption = new TextBlock { Style = Res<Style>("TzCaption"), Margin = new Thickness(2, 3, 2, 0), MaxLines = 1 };

        var root = new StackPanel();
        root.Children.Add(coverFrame);
        root.Children.Add(title);
        root.Children.Add(caption);
        Content = root;

        PointerEntered += (_, _) => { Lift(true); ShowAddPointer(true); };
        PointerExited += (_, _) => { Lift(false); ShowAddPointer(false); };
        PointerCanceled += (_, _) => { Lift(false); ShowAddPointer(false); };
        GotFocus += (_, _) => EnsureAdd();
        AddHandler(PointerPressedEvent, new PointerEventHandler((_, _) => Press(true)), true);
        AddHandler(PointerReleasedEvent, new PointerEventHandler((_, _) => Press(false)), true);
        coverFrame.SizeChanged += (_, e) => coverFrame.CenterPoint = new Vector3((float)e.NewSize.Width / 2, (float)e.NewSize.Height / 2, 0);
    }

    public static readonly DependencyProperty DataProperty = DependencyProperty.Register(
        nameof(Data), typeof(TileData), typeof(MangaTile), new PropertyMetadata(null, (d, _) => ((MangaTile)d).BindCore()));

    public TileData? Data
    {
        get => (TileData?)GetValue(DataProperty);
        set => SetValue(DataProperty, value);
    }

    public FrameworkElement CoverElement => coverFrame;

    public CoverImage Cover => cover;

    private static T Res<T>(string key) => (T)Application.Current.Resources[key];

    public void Bind(TileData data) => Data = data;

    private void BindCore()
    {
        if (Data is not { } data)
        {
            return;
        }
        title.Text = data.Title;
        caption.Text = data.Caption;
        var url = data.ThumbnailUrl;
        cover.SourceId = data.Source;
        if (cover.Url != url)
        {
            cover.Url = url;
        }
        else if (url is not null && !cover.HasSource)
        {
            cover.Reload();
        }
        badge.Visibility = data.Badge > 0 ? Visibility.Visible : Visibility.Collapsed;
        badgeText.Text = data.Badge > 99 ? "99+" : data.Badge.ToString();
        var marksKey = $"{data.InLibrary}|{data.Merged}|{data.PhoneOnly}";
        if (marksKey != marksShown)
        {
            marksShown = marksKey;
            marks.Children.Clear();
            if (data.InLibrary)
            {
                marks.Children.Add(Mark(Icons.Check, "In your library"));
            }
            if (data.Merged)
            {
                marks.Children.Add(Mark(Icons.Layers, "Merged from several sources"));
            }
            if (data.PhoneOnly)
            {
                marks.Children.Add(Mark(Icons.Smartphone, "This source reads on your phone only"));
            }
        }
        AutomationProperties.SetName(this, data.Title);
        BindAddButton(data);
        coverFrame.Translation = Vector3.Zero;
        coverFrame.Scale = Vector3.One;
    }

    public void ResetHoverState() => HideAdd();

    private void BindAddButton(TileData data)
    {
        if (data.AddAction is null)
        {
            if (add is not null && add.Visibility != Visibility.Collapsed)
            {
                add.Visibility = Visibility.Collapsed;
            }
            if (ContextFlyout is not null)
            {
                ContextFlyout = null;
            }
            return;
        }
        if (DevHarness.HoverForced || IsPointerOver || FocusState != FocusState.Unfocused)
        {
            EnsureAdd();
        }
        if (add is not null)
        {
            ApplyAdd(data);
        }
        if (data.InLibrary)
        {
            ContextFlyout = null;
            return;
        }
        addMenu ??= CreateAddMenu();
        ContextFlyout = addMenu;
    }

    private void EnsureAdd()
    {
        if (add is not null || Data is not { AddAction: not null } data)
        {
            return;
        }
        addIcon = new FontIcon { Glyph = Icons.Plus, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 16 };
        add = new Button
        {
            Style = addButtonStyle ??= (Style)Microsoft.UI.Xaml.Markup.XamlReader.Load(
                """
                <Style xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="Button">
                  <Setter Property="Background" Value="{ThemeResource TzTileAction}" />
                  <Setter Property="Foreground" Value="{ThemeResource TzTileActionIcon}" />
                  <Setter Property="BorderBrush" Value="{ThemeResource TzStroke}" />
                  <Setter Property="BorderThickness" Value="1" />
                  <Setter Property="Padding" Value="0" />
                  <Setter Property="IsTabStop" Value="False" />
                  <Setter Property="Template">
                    <Setter.Value>
                      <ControlTemplate TargetType="Button">
                        <Grid Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}" BorderThickness="{TemplateBinding BorderThickness}" CornerRadius="16">
                          <ContentPresenter Margin="{TemplateBinding Padding}" HorizontalAlignment="Center" VerticalAlignment="Center" Content="{TemplateBinding Content}" ContentTemplate="{TemplateBinding ContentTemplate}" Foreground="{TemplateBinding Foreground}" />
                        </Grid>
                      </ControlTemplate>
                    </Setter.Value>
                  </Setter>
                </Style>
                """),
            Width = 36,
            Height = 36,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(6),
            Content = addIcon,
            IsHitTestVisible = false,
        };
        add.Click += OnAddClick;
        Motion.VisualOf(add).Opacity = 0f;
        coverGrid.Children.Add(add);
        ApplyAdd(data);
    }

    private void ApplyAdd(TileData data)
    {
        add!.Visibility = Visibility.Visible;
        addIcon!.Glyph = data.InLibrary ? Icons.Check : Icons.Plus;
        add.IsEnabled = !data.InLibrary;
        AutomationProperties.SetName(add, data.InLibrary ? "In your library" : "Add to library");
        ToolTipService.SetToolTip(add, data.InLibrary ? "In your library" : "Add to library");
        HideAdd();
        if (DevHarness.HoverForced || IsPointerOver || FocusState != FocusState.Unfocused)
        {
            ShowAdd(true);
        }
    }

    private MenuFlyout CreateAddMenu()
    {
        var menu = new MenuFlyout();
        var item = new MenuFlyoutItem
        {
            Text = "Add to library",
            Icon = new FontIcon { Glyph = Icons.Plus, FontFamily = Res<FontFamily>("TzIconFont") },
        };
        item.Click += (_, _) =>
        {
            if (Data is { AddAction: { } action, InLibrary: false })
            {
                action(this);
            }
        };
        menu.Items.Add(item);
        return menu;
    }

    public bool IsAddOrigin(RoutedEventArgs e) => add is not null && ReferenceEquals(e.OriginalSource, add);

    private void OnAddClick(object sender, RoutedEventArgs e)
    {
        if (Data is { AddAction: { } action, InLibrary: false })
        {
            action(this);
        }
    }

    private void ShowAddPointer(bool on)
    {
        if (on)
        {
            EnsureAdd();
        }
        if (!DevHarness.HoverForced)
        {
            ShowAdd(on);
        }
    }

    private void ShowAdd(bool on)
    {
        if (add is null || add.Visibility != Visibility.Visible || addShown == on)
        {
            return;
        }
        addShown = on;
        add.IsHitTestVisible = on;
        var visual = Motion.VisualOf(add);
        visual.StopAnimation("Opacity");
        if (Motion.Reduced)
        {
            visual.Opacity = on ? 1f : 0f;
            return;
        }
        visual.StartAnimation("Opacity", Motion.Scalar(visual.Compositor, on ? 0f : 1f, on ? 1f : 0f, Motion.Short3, Curve.Standard));
    }

    private void HideAdd()
    {
        addShown = false;
        if (add is null)
        {
            return;
        }
        add.IsHitTestVisible = false;
        var visual = Motion.VisualOf(add);
        visual.StopAnimation("Opacity");
        visual.Opacity = 0f;
    }

    private static Border Mark(string glyph, string tip)
    {
        var b = new Border
        {
            Width = 24,
            Height = 24,
            CornerRadius = new CornerRadius(12),
            Background = new SolidColorBrush(Windows.UI.Color.FromArgb(170, 16, 17, 20)),
            Child = new FontIcon { Glyph = glyph, FontFamily = Res<FontFamily>("TzIconFont"), FontSize = 13, Foreground = new SolidColorBrush(Microsoft.UI.Colors.White) },
        };
        ToolTipService.SetToolTip(b, tip);
        return b;
    }

    private void Lift(bool on)
    {
        if (Motion.Reduced)
        {
            return;
        }
        if (on && coverFrame.Shadow is null)
        {
            coverFrame.Shadow = new ThemeShadow();
        }
        coverFrame.Translation = on ? new Vector3(0, -5, 18) : Vector3.Zero;
        coverFrame.Scale = on ? new Vector3(1.025f, 1.025f, 1) : Vector3.One;
    }

    private void Press(bool down)
    {
        if (Motion.Reduced)
        {
            return;
        }
        coverFrame.Scale = down ? new Vector3(0.975f, 0.975f, 1) : new Vector3(1.025f, 1.025f, 1);
    }
}
