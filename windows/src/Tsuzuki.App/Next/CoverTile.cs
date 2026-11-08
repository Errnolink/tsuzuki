using System.Numerics;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Tsuzuki.App.Controls;
using Tsuzuki.App.Design;
using Tsuzuki.Core.Library;

namespace Tsuzuki.App.Next;

public sealed partial class CoverTile : Button
{
    private static Style? tileStyle;
    private static Style? roundStyle;
    private readonly Border coverFrame;
    private readonly CoverImage cover;
    private readonly Border badge;
    private readonly TextBlock badgeText;
    private readonly Border phone;
    private readonly Grid progress;
    private readonly Grid overlay;
    private readonly Button continueButton;
    private readonly Button moreButton;
    private readonly TextBlock title;
    private readonly TextBlock caption;
    private bool hovered;
    private bool selectionActive;

    public CoverTile()
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

        badgeText = new TextBlock
        {
            FontSize = 12,
            FontWeight = FontWeights.SemiBold,
            Foreground = LibraryActions.Brush("TextOnAccentFillColorPrimaryBrush"),
            TextLineBounds = TextLineBounds.Tight,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        badge = new Border
        {
            Background = LibraryActions.Brush("AccentFillColorDefaultBrush"),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 0, 6, 0),
            MinWidth = 22,
            Height = 20,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 6, 6, 0),
            Child = badgeText,
        };

        phone = new Border
        {
            Width = 24,
            Height = 24,
            CornerRadius = new CornerRadius(4),
            Background = new SolidColorBrush(Windows.UI.Color.FromArgb(180, 20, 20, 20)),
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(6),
            Child = new FontIcon { Glyph = "\uE8EA", FontFamily = LibraryActions.Symbols, FontSize = 13, Foreground = new SolidColorBrush(Microsoft.UI.Colors.White) },
        };
        ToolTipService.SetToolTip(phone, "Reads on your phone only");
        AutomationProperties.SetName(phone, "Reads on your phone only");

        progress = new Grid
        {
            Height = 3,
            VerticalAlignment = VerticalAlignment.Bottom,
            Background = new SolidColorBrush(Windows.UI.Color.FromArgb(110, 0, 0, 0)),
            IsHitTestVisible = false,
            ColumnDefinitions = { new ColumnDefinition(), new ColumnDefinition() },
        };
        progress.Children.Add(new Border { Background = LibraryActions.Brush("AccentFillColorDefaultBrush") });

        continueButton = Round("\uE768", "Continue", 34, accent: true);
        continueButton.HorizontalAlignment = HorizontalAlignment.Left;
        continueButton.Click += (_, _) => _ = Log.Guard("next-tile-continue", () => Data is { } data ? MangaActions.ContinueAsync(data.MangaId, coverFrame) : Task.CompletedTask);
        moreButton = Round("\uE712", "More actions", 30, accent: false);
        moreButton.HorizontalAlignment = HorizontalAlignment.Right;
        moreButton.Click += (_, _) => ShowActions(moreButton, null);

        overlay = new Grid
        {
            Opacity = 0,
            IsHitTestVisible = false,
            Padding = new Thickness(8, 0, 8, 10),
            Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0, 0.45),
                EndPoint = new Windows.Foundation.Point(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Windows.UI.Color.FromArgb(0, 0, 0, 0), Offset = 0 },
                    new GradientStop { Color = Windows.UI.Color.FromArgb(170, 0, 0, 0), Offset = 1 },
                },
            },
            OpacityTransition = Motion.Reduced ? null : new ScalarTransition { Duration = TimeSpan.FromMilliseconds(167) },
        };
        overlay.Children.Add(continueButton);
        overlay.Children.Add(moreButton);

        var coverGrid = new Grid();
        coverGrid.Children.Add(aspect);
        coverGrid.Children.Add(overlay);
        coverGrid.Children.Add(progress);
        coverGrid.Children.Add(badge);
        coverGrid.Children.Add(phone);

        coverFrame = new Border
        {
            CornerRadius = new CornerRadius(6),
            BorderBrush = LibraryActions.Brush("CardStrokeColorDefaultBrush"),
            BorderThickness = new Thickness(1),
            Background = LibraryActions.Brush("CardBackgroundFillColorDefaultBrush"),
            Child = coverGrid,
            TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromMilliseconds(167) },
        };

        title = new TextBlock
        {
            Style = LibraryActions.Style("BodyStrongTextBlockStyle"),
            FontSize = 13,
            LineHeight = 18,
            Height = 36,
            TextWrapping = TextWrapping.Wrap,
            TextTrimming = TextTrimming.CharacterEllipsis,
            MaxLines = 2,
            Margin = new Thickness(0, 8, 0, 0),
        };
        caption = new TextBlock
        {
            Style = LibraryActions.Style("CaptionTextBlockStyle"),
            Foreground = LibraryActions.Brush("TextFillColorSecondaryBrush"),
            TextTrimming = TextTrimming.CharacterEllipsis,
            MaxLines = 1,
            Margin = new Thickness(0, 2, 0, 0),
        };

        var root = new StackPanel();
        root.Children.Add(coverFrame);
        root.Children.Add(title);
        root.Children.Add(caption);
        Content = root;

        PointerEntered += (_, _) => Hover(true);
        PointerExited += (_, _) => Hover(false);
        PointerCanceled += (_, _) => Hover(false);
        GotFocus += (_, _) => Hover(true);
        LostFocus += (_, _) => Hover(IsPointerOver);
        Click += OnClick;
        ContextRequested += (_, e) =>
        {
            e.Handled = true;
            ShowActions(this, e);
        };
    }

    public static readonly DependencyProperty DataProperty = DependencyProperty.Register(
        nameof(Data), typeof(TileData), typeof(CoverTile), new PropertyMetadata(null, (d, _) => ((CoverTile)d).Bind()));

    public TileData? Data
    {
        get => (TileData?)GetValue(DataProperty);
        set => SetValue(DataProperty, value);
    }

    public FrameworkElement CoverElement => coverFrame;

    public event EventHandler<CoverTile>? Clicked;

    public bool SelectionActive
    {
        get => selectionActive;
        set
        {
            selectionActive = value;
            IsHitTestVisible = !value;
            IsTabStop = !value;
            if (value)
            {
                Hover(false);
            }
        }
    }

    private LibraryEntry? Entry => Data?.Tag as LibraryEntry;

    private void Bind()
    {
        if (Data is not { } data)
        {
            return;
        }
        title.Text = data.Title;
        caption.Text = data.Caption;
        ToolTipService.SetToolTip(title, data.Title);
        cover.SourceId = data.Source;
        if (cover.Url != data.ThumbnailUrl)
        {
            cover.Url = data.ThumbnailUrl;
        }
        else if (data.ThumbnailUrl is not null && !cover.HasSource)
        {
            cover.Reload();
        }
        badge.Visibility = data.Badge > 0 ? Visibility.Visible : Visibility.Collapsed;
        badgeText.Text = data.Badge > 999 ? "999+" : data.Badge.ToString();
        phone.Visibility = data.PhoneOnly ? Visibility.Visible : Visibility.Collapsed;
        var (read, total) = Entry is { } entry ? LibraryActions.Progress(entry) : (0, 0);
        if (read > 0 && read < total)
        {
            progress.ColumnDefinitions[0].Width = new GridLength(read, GridUnitType.Star);
            progress.ColumnDefinitions[1].Width = new GridLength(total - read, GridUnitType.Star);
            progress.Visibility = Visibility.Visible;
        }
        else
        {
            progress.Visibility = Visibility.Collapsed;
        }
        var name = data.Title;
        if (data.Badge > 0)
        {
            name += $", {data.Badge} unread";
        }
        if (data.Caption.Length > 0)
        {
            name += ", " + data.Caption;
        }
        if (data.PhoneOnly)
        {
            name += ", reads on your phone only";
        }
        AutomationProperties.SetName(this, name);
        Hover(!selectionActive && (IsPointerOver || FocusState != FocusState.Unfocused));
    }

    private void OnClick(object sender, RoutedEventArgs e)
    {
        if (ReferenceEquals(e.OriginalSource, continueButton) || ReferenceEquals(e.OriginalSource, moreButton) || Data is null)
        {
            return;
        }
        Clicked?.Invoke(this, this);
    }

    private void ShowActions(FrameworkElement target, ContextRequestedEventArgs? e)
    {
        if (Data is { } data)
        {
            LibraryActions.ShowFlyout(target, e, data.MangaId, data.Title, Entry, coverFrame);
        }
    }

    private void Hover(bool on)
    {
        on &= !selectionActive;
        if (on == hovered)
        {
            return;
        }
        hovered = on;
        overlay.Opacity = on ? 1 : 0;
        overlay.IsHitTestVisible = on;
        if (Motion.Reduced)
        {
            return;
        }
        if (on && coverFrame.Shadow is null)
        {
            coverFrame.Shadow = new ThemeShadow();
        }
        coverFrame.Translation = on ? new Vector3(0, -3, 16) : Vector3.Zero;
    }

    private static Button Round(string glyph, string label, double size, bool accent)
    {
        roundStyle ??= (Style)Microsoft.UI.Xaml.Markup.XamlReader.Load(
            """
            <Style xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" TargetType="Button">
              <Setter Property="Padding" Value="0" />
              <Setter Property="IsTabStop" Value="False" />
              <Setter Property="BorderThickness" Value="0" />
              <Setter Property="Template">
                <Setter.Value>
                  <ControlTemplate TargetType="Button">
                    <Grid x:Name="Root" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="{TemplateBinding Background}" CornerRadius="999">
                      <VisualStateManager.VisualStateGroups>
                        <VisualStateGroup x:Name="CommonStates">
                          <VisualState x:Name="Normal" />
                          <VisualState x:Name="PointerOver">
                            <VisualState.Setters>
                              <Setter Target="Root.Opacity" Value="0.88" />
                            </VisualState.Setters>
                          </VisualState>
                          <VisualState x:Name="Pressed">
                            <VisualState.Setters>
                              <Setter Target="Root.Opacity" Value="0.72" />
                            </VisualState.Setters>
                          </VisualState>
                        </VisualStateGroup>
                      </VisualStateManager.VisualStateGroups>
                      <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center" Content="{TemplateBinding Content}" Foreground="{TemplateBinding Foreground}" />
                    </Grid>
                  </ControlTemplate>
                </Setter.Value>
              </Setter>
            </Style>
            """);
        var button = new Button
        {
            Style = roundStyle,
            Width = size,
            Height = size,
            VerticalAlignment = VerticalAlignment.Bottom,
            Background = accent ? LibraryActions.Brush("AccentFillColorDefaultBrush") : new SolidColorBrush(Windows.UI.Color.FromArgb(150, 32, 32, 32)),
            Foreground = accent ? LibraryActions.Brush("TextOnAccentFillColorPrimaryBrush") : new SolidColorBrush(Microsoft.UI.Colors.White),
            Content = LibraryActions.Glyph(glyph, accent ? 14 : 16),
        };
        AutomationProperties.SetName(button, label);
        ToolTipService.SetToolTip(button, label);
        return button;
    }
}
