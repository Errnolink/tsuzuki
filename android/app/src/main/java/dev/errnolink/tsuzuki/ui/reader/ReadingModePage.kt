package dev.errnolink.tsuzuki.ui.reader

import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

@Composable
internal fun ReadingModePage(screenModel: ReaderSettingsScreenModel) {
    TsuzukiText(stringResource(MR.strings.pref_category_for_this_series), style = TsuzukiTheme.typography.headline)
    val manga by screenModel.mangaFlow.collectAsState()

    val readingMode = remember(manga) { ReadingMode.fromPreference(manga?.readingMode?.toInt()) }
    ReaderChoice(
        "Reading mode",
        ReadingMode.entries.map { stringResource(it.stringRes) },
        ReadingMode.entries.indexOf(readingMode),
        { screenModel.onChangeReadingMode(ReadingMode.entries[it]) },
    )

    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    ReaderChoice(
        stringResource(MR.strings.rotation_type),
        ReaderOrientation.entries.map { stringResource(it.stringRes) },
        ReaderOrientation.entries.indexOf(orientation),
        { screenModel.onChangeOrientation(ReaderOrientation.entries[it]) },
    )

    val viewer by screenModel.viewerFlow.collectAsState()
    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(
            screenModel,
            // KMK -->
            readingMode,
            // KMK <--
        )
        // SY -->
        WebtoonWithGapsViewerSettings(screenModel)
        // SY <--
    } else {
        PagerViewerSettings(screenModel)
    }
}

@Composable
private fun PagerViewerSettings(screenModel: ReaderSettingsScreenModel) {
    TsuzukiText(stringResource(MR.strings.pager_viewer), style = TsuzukiTheme.typography.headline)

    val navigationModePager by screenModel.preferences.navigationModePager().collectAsState()
    val pagerNavInverted by screenModel.preferences.pagerNavInverted().collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = screenModel.preferences.navigationModePager()::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = screenModel.preferences.pagerNavInverted()::set,
    )

    val imageScaleType by screenModel.preferences.imageScaleType().collectAsState()
    ReaderChoice(
        stringResource(MR.strings.pref_image_scale_type),
        ReaderPreferences.ImageScaleType.map { stringResource(it) },
        imageScaleType - 1,
        { screenModel.preferences.imageScaleType().set(it + 1) },
    )

    val zoomStart by screenModel.preferences.zoomStart().collectAsState()
    ReaderChoice(
        stringResource(MR.strings.pref_zoom_start),
        ReaderPreferences.ZoomStart.map { stringResource(it) },
        zoomStart - 1,
        { screenModel.preferences.zoomStart().set(it + 1) },
    )

    // SY -->
    val pageLayout by screenModel.preferences.pageLayout().collectAsState()
    ReaderChoice(
        stringResource(SYMR.strings.page_layout),
        ReaderPreferences.PageLayouts.map { stringResource(it) },
        pageLayout,
        screenModel.preferences.pageLayout()::set,
    )
    // SY <--

    // KMK -->
    ReaderToggle(label = stringResource(KMR.strings.pref_viewer_nav_smaller_tap_zone), pref = screenModel.preferences.smallerTapZone())
    // KMK <--

    ReaderToggle(label = stringResource(MR.strings.pref_crop_borders), pref = screenModel.preferences.cropBorders())

    // KMK -->
    if (imageScaleType in ReaderPreferences.zoomWideImagesAllowedList) {
        // KMK <--
        ReaderToggle(label = stringResource(MR.strings.pref_landscape_zoom), pref = screenModel.preferences.landscapeZoom())
    }

    ReaderToggle(label = stringResource(MR.strings.pref_navigate_pan), pref = screenModel.preferences.navigateToPan())

    val dualPageSplitPaged by screenModel.preferences.dualPageSplitPaged().collectAsState()
    ReaderToggle(label = stringResource(MR.strings.pref_dual_page_split), pref = screenModel.preferences.dualPageSplitPaged())

    if (dualPageSplitPaged) {
        ReaderToggle(label = stringResource(MR.strings.pref_dual_page_invert), pref = screenModel.preferences.dualPageInvertPaged())
    }

    val dualPageRotateToFit by screenModel.preferences.dualPageRotateToFit().collectAsState()
    ReaderToggle(label = stringResource(MR.strings.pref_page_rotate), pref = screenModel.preferences.dualPageRotateToFit())

    if (dualPageRotateToFit) {
        ReaderToggle(label = stringResource(MR.strings.pref_page_rotate_invert), pref = screenModel.preferences.dualPageRotateToFitInvert())
    }

    // SY -->
    ReaderToggle(label = stringResource(MR.strings.pref_page_transitions), pref = screenModel.preferences.pageTransitionsPager())

    ReaderToggle(label = stringResource(SYMR.strings.invert_double_pages), pref = screenModel.preferences.invertDoublePages())

    // KMK -->
    ReaderToggle(label = stringResource(KMR.strings.pref_paged_disable_zoom_in), pref = screenModel.preferences.pagedDisableZoomIn())
    val pagedDisableZoomIn by screenModel.preferences.pagedDisableZoomIn().collectAsState()
    if (!pagedDisableZoomIn) {
        ReaderToggle(label = stringResource(MR.strings.pref_double_tap_zoom), pref = screenModel.preferences.pagedDoubleTapZoomEnabled())
    }
    // KMK <--

    val centerMarginType by screenModel.preferences.centerMarginType().collectAsState()
    ReaderChoice(
        stringResource(SYMR.strings.pref_center_margin),
        ReaderPreferences.CenterMarginTypes.map { stringResource(it) },
        centerMarginType,
        screenModel.preferences.centerMarginType()::set,
    )
    // SY <--
}

@Composable
private fun WebtoonViewerSettings(
    screenModel: ReaderSettingsScreenModel,
    // KMK -->
    readingMode: ReadingMode,
    // KMK <--
) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    TsuzukiText(stringResource(MR.strings.webtoon_viewer), style = TsuzukiTheme.typography.headline)

    val navigationModeWebtoon by screenModel.preferences.navigationModeWebtoon().collectAsState()
    val webtoonNavInverted by screenModel.preferences.webtoonNavInverted().collectAsState()
    TapZonesItems(
        selected = navigationModeWebtoon,
        onSelect = screenModel.preferences.navigationModeWebtoon()::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = screenModel.preferences.webtoonNavInverted()::set,
    )

    // KMK -->
    val webtoonScaleTypePref = screenModel.preferences.webtoonScaleType()
    val webtoonScaleType by webtoonScaleTypePref.collectAsState()
    val webtoonSmartScaleLongStripGap = screenModel.preferences.longStripGapSmartScale().get()
    if (readingMode != ReadingMode.CONTINUOUS_VERTICAL || webtoonSmartScaleLongStripGap) {
        ReaderChoice(
            stringResource(KMR.strings.pref_webtoon_scale_type),
            ReaderPreferences.WebtoonScaleType.entries.map { stringResource(it.titleRes) },
            ReaderPreferences.WebtoonScaleType.entries.indexOf(webtoonScaleType),
            { webtoonScaleTypePref.set(ReaderPreferences.WebtoonScaleType.entries[it]) },
        )
    }
    // KMK <--

    val webtoonSidePadding by screenModel.preferences.webtoonSidePadding().collectAsState()
    ReaderSettingSlider(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.let { it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX },
        label = stringResource(MR.strings.pref_webtoon_side_padding),
        valueString = numberFormat.format(webtoonSidePadding / 100f),
        onChange = { screenModel.preferences.webtoonSidePadding().set(it) },
    )

    // KMK -->
    ReaderToggle(label = stringResource(KMR.strings.pref_viewer_nav_smaller_tap_zone), pref = screenModel.preferences.smallerTapZone())
    // KMK <--

    ReaderToggle(label = stringResource(MR.strings.pref_crop_borders), pref = screenModel.preferences.cropBordersWebtoon())

    // SY -->
    ReaderToggle(label = stringResource(SYMR.strings.pref_smooth_scroll), pref = screenModel.preferences.smoothAutoScroll())

    ReaderToggle(label = stringResource(MR.strings.pref_page_transitions), pref = screenModel.preferences.pageTransitionsWebtoon())
    // SY <--

    val dualPageSplitWebtoon by screenModel.preferences.dualPageSplitWebtoon().collectAsState()
    ReaderToggle(label = stringResource(MR.strings.pref_dual_page_split), pref = screenModel.preferences.dualPageSplitWebtoon())

    if (dualPageSplitWebtoon) {
        ReaderToggle(label = stringResource(MR.strings.pref_dual_page_invert), pref = screenModel.preferences.dualPageInvertWebtoon())
    }

    val dualPageRotateToFitWebtoon by screenModel.preferences.dualPageRotateToFitWebtoon().collectAsState()
    ReaderToggle(label = stringResource(MR.strings.pref_page_rotate), pref = screenModel.preferences.dualPageRotateToFitWebtoon())

    if (dualPageRotateToFitWebtoon) {
        ReaderToggle(label = stringResource(MR.strings.pref_page_rotate_invert), pref = screenModel.preferences.dualPageRotateToFitInvertWebtoon())
    }

    ReaderToggle(label = stringResource(MR.strings.pref_double_tap_zoom), pref = screenModel.preferences.webtoonDoubleTapZoomEnabled())
    // KMK -->
    ReaderToggle(label = stringResource(KMR.strings.pref_pinch_to_zoom), pref = screenModel.preferences.webtoonPinchToZoomEnabled())
    // KMK <--
    ReaderToggle(label = stringResource(MR.strings.pref_webtoon_disable_zoom_out), pref = screenModel.preferences.webtoonDisableZoomOut())
}

// SY -->
@Composable
private fun WebtoonWithGapsViewerSettings(screenModel: ReaderSettingsScreenModel) {
    TsuzukiText(stringResource(MR.strings.vertical_plus_viewer), style = TsuzukiTheme.typography.headline)

    ReaderToggle(label = stringResource(MR.strings.pref_crop_borders), pref = screenModel.preferences.cropBordersContinuousVertical())
}
// SY <--

@Composable
private fun TapZonesItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    ReaderChoice(
        stringResource(MR.strings.pref_viewer_nav),
        ReaderPreferences.TapZones.map { stringResource(it) },
        selected,
        onSelect,
    )

    if (selected != 5) {
        ReaderChoice(
            stringResource(MR.strings.pref_read_with_tapping_inverted),
            ReaderPreferences.TappingInvertMode.entries.map { stringResource(it.titleRes) },
            ReaderPreferences.TappingInvertMode.entries.indexOf(invertMode),
            { onSelectInvertMode(ReaderPreferences.TappingInvertMode.entries[it]) },
        )
    }
}
