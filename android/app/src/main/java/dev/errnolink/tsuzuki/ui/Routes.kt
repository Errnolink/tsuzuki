package dev.errnolink.tsuzuki.ui

object Routes {

    data class Route(
        val upstream: String,
        val ours: String,
        val hook: String,
    )

    val all: List<Route> = listOf(
        Route(
            upstream = "eu.kanade.tachiyomi.ui.main.MainActivity",
            ours = "dev.errnolink.tsuzuki.ui.shell.TsuzukiShell",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt setContent",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.home.HomeScreen",
            ours = "dev.errnolink.tsuzuki.ui.shell.TsuzukiShell",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.reader.ReaderActivity",
            ours = "dev.errnolink.tsuzuki.ui.reader.ReaderAppBars",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt setComposeOverlay",
        ),
        Route(
            upstream = "eu.kanade.presentation.reader.ChapterListDialog",
            ours = "dev.errnolink.tsuzuki.ui.reader.ChapterListDialog",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt setComposeOverlay",
        ),
        Route(
            upstream = "mihon.feature.upcoming.UpcomingScreen",
            ours = "dev.errnolink.tsuzuki.ui.updates.UpcomingScreenContent",
            hook = "app/src/main/java/mihon/feature/upcoming/UpcomingScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.library.LibraryTab",
            ours = "dev.errnolink.tsuzuki.ui.library.TsuzukiLibraryScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.updates.UpdatesTab",
            ours = "dev.errnolink.tsuzuki.ui.updates.UpdateScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.history.HistoryTab",
            ours = "dev.errnolink.tsuzuki.ui.history.HistoryScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoryTab.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.BrowseTab",
            ours = "dev.errnolink.tsuzuki.ui.browse.BrowseTabContent",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.more.MoreTab",
            ours = "dev.errnolink.tsuzuki.ui.more.MoreScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.source.SourcesTab",
            ours = "dev.errnolink.tsuzuki.ui.browse.SourcesScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/SourcesTab.kt sourcesTab",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.extension.ExtensionsTab",
            ours = "dev.errnolink.tsuzuki.ui.browse.ExtensionScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt extensionsTab",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.feed.FeedTab",
            ours = "dev.errnolink.tsuzuki.ui.browse.FeedScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/FeedTab.kt feedTab",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.feed.FeedTab",
            ours = "dev.errnolink.tsuzuki.ui.feed.FeedTabContent",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/feed/FeedTab.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen",
            ours = "dev.errnolink.tsuzuki.ui.browse.GlobalSearchScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen",
            ours = "dev.errnolink.tsuzuki.ui.browse.BrowseSourceContent",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen",
            ours = "dev.errnolink.tsuzuki.ui.browse.SourceFeedScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/feed/SourceFeedScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.source.SourcesScreen",
            ours = "dev.errnolink.tsuzuki.ui.browse.BrowseTabWrapper",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/SourcesScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSourceSearchScreen",
            ours = "dev.errnolink.tsuzuki.ui.browse.BrowseSourceContent",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/search/MigrateSourceSearchScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.MangaScreen",
            ours = "dev.errnolink.tsuzuki.ui.details.MangaScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.category.CategoryScreen",
            ours = "dev.errnolink.tsuzuki.ui.category.CategoryScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/category/CategoryScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.download.DownloadQueueScreen",
            ours = "dev.errnolink.tsuzuki.ui.downloads.DownloadQueueScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadQueueScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.more.OnboardingScreen",
            ours = "dev.errnolink.tsuzuki.ui.onboarding.OnboardingScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/more/OnboardingScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.stats.StatsScreen",
            ours = "dev.errnolink.tsuzuki.ui.more.statsSections",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/stats/StatsScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsMainScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsMainScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsAppearanceScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsDataScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsDataScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt Content",
        ),

        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsMangadexScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsMangadexScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMangadexScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsSearchScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsSearchScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSearchScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsSecurityScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsTrackingScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSettingsAdvancedScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.about.AboutScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiAboutScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiClearDatabaseScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/advanced/ClearDatabaseScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiAppLanguageScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/appearance/AppLanguageScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.ExtensionStoresScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/ExtensionStoresScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiCreateBackupScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiRestoreBackupScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/data/RestoreBackupScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.data.SyncSettingsSelector",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSyncSettingsSelector",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/data/SyncSettingsSelector.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.data.SyncTriggerOptionsScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.TsuzukiSyncTriggerOptionsScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/data/SyncTriggerOptionsScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.about.OpenSourceLicensesScreen",
            ours = "dev.errnolink.tsuzuki.ui.more.about.TsuzukiOpenSourceLicensesScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/about/OpenSourceLicensesScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.about.OpenSourceLibraryLicenseScreen",
            ours = "dev.errnolink.tsuzuki.ui.more.about.TsuzukiOpenSourceLibraryLicenseScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/about/OpenSourceLibraryLicenseScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.debug.BackupSchemaScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.debug.TsuzukiBackupSchemaScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/debug/BackupSchemaScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.presentation.more.settings.screen.debug.WorkerInfoScreen",
            ours = "dev.errnolink.tsuzuki.ui.settings.debug.TsuzukiWorkerInfoScreen",
            hook = "app/src/main/java/eu/kanade/presentation/more/settings/screen/debug/WorkerInfoScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.category.genre.SortTagScreen",
            ours = "dev.errnolink.tsuzuki.ui.category.genre.TsuzukiSortTagScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/category/genre/SortTagScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreen",
            ours = "dev.errnolink.tsuzuki.ui.category.sources.TsuzukiSourceCategoryScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/category/sources/SourceCategoryScreen.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackDateRemoverScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackDateRemoverScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackDateSelectorScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackDateSelectorScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackStatusSelectorScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackStatusSelectorScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackScoreSelectorScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackScoreSelectorScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackChapterSelectorScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackChapterSelectorScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackerSearchScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackerSearchScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.track.TrackerRemoveScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.track.TrackerRemoveScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt Content",
        ),
        Route(
            upstream = "eu.kanade.tachiyomi.ui.manga.PaletteScreen",
            ours = "dev.errnolink.tsuzuki.ui.manga.PaletteScreen",
            hook = "app/src/main/java/eu/kanade/tachiyomi/ui/manga/PaletteScreen.kt Content",
        ),
    )
}
