# Upstream base

| Field | Value |
|---|---|
| Project | Komikku (komikku-app/komikku) |
| Version | v1.14.1 |
| Tag commit | e30ff83265d7affcc14e172ea9eadf469db1f4aa |
| Source | GitHub release source zip `komikku-v1.14.1.zip` (copy in `../.downloads/`) |
| Imported | 2026-10-05 |

Removed on import: `.github/`, `.gemini/`, `.idea/`, `.weblate/`, `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`, `renovate.json`. Upstream `AGENTS.md` and `README.md` moved to `docs/upstream/`.

To take upstream changes: download the newer release zip, diff it against this tag's zip, and apply the relevant parts on a `feat/upstream-<version>` branch.

## Vendor branch

`vendor/komikku` points at the pristine import commit `eb26a2e` (Komikku v1.14.1). It is the merge base for every future upstream release: import a newer release zip onto that branch (replace tree, keep `.git`, commit `vendor: Komikku vX.Y.Z`), then `git merge vendor/komikku` on the product branch. Conflicts should appear only at the hook points listed below, in required-logic files, and in deleted files (resolve as "keep deleted").

Restore source used by the isolation migration: `git show eb26a2e:<path>` is pristine for every file; the four files that pristine shares with the rebrand (`AboutScreen.kt`, `SettingsAdvancedScreen.kt`, `SettingsSecurityScreen.kt`, `onboarding/PermissionStep.kt`) are restored from `b4c0c05` instead (pristine + updater/telemetry prune).

## Local deviations

- 2026-10-06: Use the published `com.github.arkon:FlexibleAdapter:844a07002c` AAR instead of upstream's unavailable `com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533`. The [unavailable commit](https://github.com/arkon/FlexibleAdapter/commit/c80135339bcff5f7f8c2c2380329dfc155b26232) directly follows this parent and only changes dependency wiring for the unused livedata module, not the core adapter code. [Published artifact metadata](https://jitpack.io/com/github/arkon/FlexibleAdapter/844a07002c/FlexibleAdapter-844a07002c.module) identifies the Android release AAR. This repairs release dependency resolution without introducing another library.

- 2026-10-06 behaviour fix (user-reported broken swipe page turns, `docs/research/18-ios27-reference-and-round2.md` §7 issue 15): the reader keeps the upstream layout — `readerContainer` and `navigationOverlay` stay native siblings inside `binding.root` with the `composeOverlay` ComposeView on top. The `ReaderActivity` re-parenting (`removeView`/`addView` into `readerContainer`) and the `AndroidView(factory = { readerContainer }, Modifier.glassSource(glass))` block are gone; our reader chrome in `dev.errnolink.tsuzuki.ui.reader` draws its glass with the tint fallback instead of live-sampling the page, so the View pager/webtoon keep native touch handling.

- 2026-10-07 reader canvas default (`ReaderPreferences.readerTheme`): change the unset preference from black (`1`) to automatic (`3`) so upstream page-edge background selection blends fit-mode letterboxing with the artwork. Explicit saved theme choices remain untouched. This is a required preference-default deviation, not a reader-engine or fit/zoom change.
- 2026-10-07 clean-build fix: `buildSrc/src/main/kotlin/mihon/buildlogic/tasks/LocalesConfigTask.kt` generates `locales_config.xml` in the task action, with declared resource inputs and output file. Upstream wrote it during configuration, so `clean :app:assembleDebug` deleted it before resource linking.

## Migration inventory (2026-10-06, `feat/isolate`, base `feat/ui-lead` @ 002a711)

Classification of every file in `git diff --name-status eb26a2e HEAD`:

- **(a)** Tsuzuki presentation: moved to `app/src/main/java/dev/errnolink/tsuzuki/ui/<area>/`, upstream file restored to pristine.
- **(b)** Hook point: upstream file kept, edited only to call our code (see "Hook points" below for the surviving list).
- **(c)** Required logic/rebrand: kept as our change in upstream code, with reason.
- **(d)** Deletion: upstream file removed; nothing restored references it.

### (a) Presentation moved to `dev.errnolink.tsuzuki.ui.*`

Settings (`ui.settings`):

| Upstream file | Notes |
|---|---|
| `presentation/more/settings/PreferenceItem.kt` | our preference row renderer |
| `presentation/more/settings/SettingsDialogControls.kt` (A) | |
| `presentation/more/settings/SettingsForms.kt` (A) | defines our `PreferenceScaffold`/`SettingsScaffold` |
| `presentation/more/settings/TsuzukiSettingsUi.kt` (A) | |
| `presentation/more/settings/screen/{ConfigureExhDialog, SettingsAdvancedScreen, SettingsAppearanceScreen, SettingsConnectionScreen, SettingsDataScreen, SettingsDiscordAccountsScreen, SettingsDiscordScreen, SettingsEhScreen, SettingsMainScreen, SettingsMangadexScreen, SettingsSearchScreen, SettingsSecurityScreen, SettingsTrackingScreen}.kt` | Voyager `Screen` objects; our object `Tsuzuki…` mirrors them, restored upstream object hooks `Content()` to ours |
| `presentation/more/settings/screen/about/AboutScreen.kt` | restored from `b4c0c05` (updater refs pruned by rebrand) |
| `presentation/more/settings/screen/advanced/ClearDatabaseScreen.kt` | |
| `presentation/more/settings/screen/appearance/AppLanguageScreen.kt` | |
| `presentation/more/settings/screen/browse/components/{ExtensionStoresContent, ExtensionStoresDialogs, ExtensionStoresScreen}.kt` | |
| `presentation/more/settings/screen/data/{CreateBackupScreen, RestoreBackupScreen, StorageInfo, SyncSettingsSelector, SyncTriggerOptionsScreen}.kt` | |
| `presentation/more/settings/widget/{ConnectionPreferenceWidget, EditTextPreferenceWidget, InfoWidget, ListPreferenceWidget, MultiSelectListPreferenceWidget, SwitchPreferenceWidget, TextPreferenceWidget, TrackingPreferenceWidget, TriStateListDialog}.kt` | our row widgets; pristine widgets restored upstream |
| `presentation/more/settings/widget/TsuzukiRows.kt` (A) | |

Restored to pristine (were deleted, pristine code still references them): `settings/PreferenceScreen.kt`, `settings/PreferenceScaffold.kt`, `settings/widget/{AppThemeModePreferenceWidget, AppThemePreferenceWidget, BasePreferenceWidget, PreferenceGroupHeader, ThemeColorPickerWidget}.kt`, `settings/screen/appearance/AppCustomThemeColorPickerScreen.kt`.

More / onboarding (`ui.more`, `ui.onboarding`):

| Upstream file | Notes |
|---|---|
| `presentation/more/LogoHeader.kt` | restored copy keeps the rebrand icon swap |
| `presentation/more/MoreScreen.kt` | caller `ui/more/MoreTab.kt` hooked |
| `presentation/more/onboarding/{GuidesStep, OnboardingScreen, PermissionStep, StorageStep, ThemeStep}.kt` | restored `PermissionStep` from `b4c0c05` |
| `presentation/more/stats/StatsScreenContent.kt` | caller `ui/stats/StatsScreen.kt` hooked |

Library (`ui.library`): `presentation/library/{DeleteLibraryMangaDialog, LibrarySettingsDialog, TsuzukiLibraryScreen}.kt`; caller `ui/library/LibraryTab.kt` hooked.

Updates / history (`ui.updates`, `ui.history`): `presentation/updates/{UpdatesDeleteConfirmationDialog, UpdatesFilterDialog, UpdatesScreen, UpdatesUiItem}.kt`; `presentation/history/{HistoryScreen, components/HistoryDialogs, components/HistoryFilterDialog, components/HistoryItem}.kt`; callers `ui/updates/UpdatesTab.kt`, `ui/history/HistoryTab.kt` hooked.

Browse (`ui.browse`): `presentation/browse/{BrowseSourceScreen, BrowseTabWrapper, ExtensionsScreen, FeedScreen, GlobalSearchScreen, SourceFeedScreen, SourcesScreen}.kt`; `presentation/browse/components/{GlobalSearchCardRow, GlobalSearchResultItems}.kt`; callers `ui/browse/BrowseTab.kt`, `ui/browse/source/SourcesTab.kt`, `ui/browse/source/browse/BrowseSourceScreen.kt`, `ui/browse/source/globalsearch/GlobalSearchScreen.kt`, `ui/browse/extension/ExtensionsScreen.kt` hooked. New ours-only screens moved: `ui/browse/SourceMigrationScreen.kt` (A).

Details (`ui.details`): `presentation/manga/{MangaScreen, ChapterSettingsDialog}.kt`; `presentation/manga/components/{ChapterDownloadIndicator, DetailControls (A), MangaBottomActionMenu, MangaChapterListItem, MangaCoverDialog, MangaInfoHeader}.kt`; callers `ui/manga/MangaScreen.kt` hooked; `ui/manga/MangaCoversScreen.kt` (A) moved to ours. `ui/manga/PaletteScreen.kt` restored to pristine (pristine `MangaScreen` still references it; the seed-colour path is dormant because `themeCoverBased` defaults false).

Reader chrome (`ui.reader`): `presentation/reader/{OrientationSelectDialog, ReadingModeSelectDialog}.kt`; `presentation/reader/appbars/{ExhUtils, ReaderAppBars, ReaderBottomBar, ReaderTopBar}.kt`; `presentation/reader/components/{ChapterNavigator, ReaderSlider (A)}.kt`; `presentation/reader/settings/{ColorFilterPage, GeneralSettingsPage, ReaderSettingsControls (A), ReaderSettingsDialog, ReadingModePage}.kt`; caller `ui/reader/ReaderActivity.kt` hooked. `reader/components/ModeSelectionDialog.kt` restored to pristine.

Categories (`ui.category`): `presentation/category/{CategoryScreen, components/CategoryDialogs, components/CategoryListItem}.kt` moved; caller `ui/category/CategoryScreen.kt` hooked via import swap; the routed screens repoint `ChangeCategoryDialog` to ours (Library/History/Manga/BrowseSource hooks). Pristine callers (BulkFavoriteDialogs, biometric/genre/source-category sub-screens, `exh/md/follows`) keep the upstream Material dialogs because those screens are themselves pristine.

Downloads (`ui.downloads`): the redesigned `ui/download/DownloadQueueScreen.kt` content moved to ours; upstream file restored pristine with a `Content()` → `TsuzukiDownloadQueueScreen.Content()` hook; the upstream RecyclerView adapter/holders and `res/layout/download_*.xml` were never deleted and stay pristine.

Shell / shared (`ui.shell`): our `presentation/components/{TsuzukiShell, ShellContentScaffold}.kt` (added) plus our redesigned `Banners.kt` and `TabbedScreen.kt` moved to `ui.shell`; upstream `TabbedScreen.kt` and `Banners.kt` restored pristine. Hooks `HomeScreen`, `MainActivity`, `BrowseTab`, `SourcesTab`/`ExtensionsTab`/`FeedTab` (our `TabContent` feeds our `TabbedScreen`), extension `ExtensionsScreen`, `BrowseSourceScreen`, `SourceFilterDialog` import ours; `MigrateSourceTab` returns our `TabContent` because its consumers route through `BrowseTabWrapper`. Pristine callers keep upstream `WarningBanner`/`AppStateBanners` (only our screens and `MainActivity` render ours).

### (b) Hook points (final list after the migration)

See "Hook points" section below (file, function, reason). One line summary: Voyager `Screen`/`Tab` `Content()` delegations, `MainActivity`/`ReaderActivity` `setContent`, `HomeScreen` shell host, `TachiyomiTheme` token bridge, `UiPreferences` defaults, DI modules, build/registration files.

### (c) Required logic / rebrand (kept in upstream files)

Rebrand: `App.kt`, `util/system/BuildConfig.kt`, `data/backup/BackupNotifier.kt`, `data/download/{DownloadJob, DownloadNotifier}.kt`, `data/download/Downloader.kt` (also the cold-start ANR fix: lazy XML), `data/library/LibraryUpdateNotifier.kt`, `data/notification/{NotificationReceiver, Notifications}.kt` (updater ids removed), `data/connections/discord/DiscordRPCService.kt`, `extension/api/ExtensionUpdateNotifier.kt`, `extension/util/ExtensionInstallService.kt`, `exh/eh/EHentaiUpdateNotifier.kt`, `ui/reader/SaveImageNotifier.kt`, `presentation/more/LogoHeader.kt` (restored copy keeps `ic_tsuzuki`), `data/track/kitsu/KitsuInterceptor.kt` (User-Agent names Tsuzuki), all `res/` icon/splash/colour files, `app/src/main/AndroidManifest.xml`, `app/src/debug/**`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/baseline-prof.txt`, `build.gradle.kts`, `buildSrc/…/BuildConfig.kt`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `macrobenchmark/**`, `i18n*/…/strings.xml` (Tsuzuki strings), `designsystem/**` (our module).

SyncYomi v2 (protocol v2 client + triggers): `domain/sync/SyncCategoryState.kt` (A), `data/sync/{SyncDataJob, SyncManager, SyncNotifier, models/SyncTriggerOptions, service/BackupRequestBody (A), service/SyncService, service/SyncYomiSyncService, service/GoogleDriveSyncService, service/WebDavSyncService}.kt`, `domain/sync/SyncCategoryState` interplay in `data/sqldelight/{categories, chapters, mangas, merged}.sq` + `migrations/47.sqm`, `data/category/CategoryMapper.kt`, `data/category/CategoryRepositoryImpl.kt`, `data/manga/MangaRepositoryImpl.kt`, `domain/category/{DeleteCategory, model/Category, model/CategoryUpdate}.kt`, backup model defaults `data/backup/models/**` (all-optional proto ctors for v2 payloads), `data/backup/restore/BackupRestorer.kt`, `restore/restorers/{CategoriesRestorer, MangaRestorer, MergedReferenceReconciliation (A), PreferenceRestorer}.kt`, `eu/kanade/domain/sync/SyncPreferences.kt`, `ui/reader/ReaderViewModel.kt` (debounced page/lifecycle sync triggers).

Neko / MangaDex parity: `exh/md/**` (new handlers, DTOs, preferences; files adapted from Neko carry a first-line notice), `eu/kanade/domain/chapter/interactor/SetReadStatus.kt`, `SyncChaptersWithSource.kt`, `eu/kanade/domain/manga/interactor/UpdateManga.kt`, `domain/chapter/interactor/{UpdateChapter, GetMergedChaptersByMangaId}.kt`, `domain/chapter/service/ChapterReadSync.kt` (A), `source/online/all/MangaDex.kt`, `exh/recs/sources/{MangaDexRecommendedPagingSource (A), RecommendationPagingSource}.kt`, `source-api/…/MangaDexSearchMetadata.kt`, `domain/exh/source/ExhPreferences.kt` (updater pref removed), `eu/kanade/domain/DomainModule.kt`, `di/{AppModule (also ANR prewarm), PreferenceModule}.kt`, `mihon/core/migration/migrations/Migrations.kt` (migration 47 registered, `SetupAppUpdateMigration` dropped), tests under `app/src/test/**`.

Round 3 Neko backup compatibility: `data/backup/models/{BackupManga,BackupChapter}.kt` add donor protobuf tags 900–907 without renumbering SY/Komikku tags; `BackupChapter` becomes a data class for non-mutating migration copies. `restore/BackupRestorer.kt` delegates Neko merge expansion to `dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper`, resolving only installed/stub source identities and surfacing missing-extension warnings. `restore/restorers/MangaRestorer.kt` maps exclusions/custom artwork, persists imported Neko memo fields, hides resolved source children, reconciles converted SY references, and moves previously unresolved progress to its canonical child on re-restore. No source IDs are invented; unresolved descriptors/progress remain in memo and the restore report names the required extension.

Round 3 authentication: `exh/md/network/MangaDexAuthInterceptor.kt` invokes the isolated session notifier only after persistent refresh rejection (400/401/403); transient HTTP/network failures retain tokens. Successful authentication dismisses the notice. `AndroidManifest.xml` registers MangaDex settings and author/group/list intents; `MainActivity.handleIntentAction` delegates them to `mangadex/MangaDexIntents`.

Round 3 bulk tracking: `eu/kanade/domain/track/interactor/RefreshTracks.kt` propagates cancellation and accepts `notifyProgress=false` for the isolated `mangadex/TrackingSyncJob`; interactive callers retain progress toasts. The job reuses the existing tracker progress preference.

Round 3 library filters: `LibraryPreferences` and `ui/library/LibraryScreenModel` add persisted tri-state filter inputs to the existing reactive predicate and active-filter indicator. Existing performance subscription/debounce/download batching is preserved.

Round 3 chapter filtering: `data/…/view/excludedScanlatorsView.sq` is the shared ALL/ANY exclusion rule; `chapters.sq`, `mangas.sq`, and library/history/update views consume it. `GetAvailableScanlators` and `GetExcludedScanlators` expose individual collaboration credits; `SyncChaptersWithSource` uses effective exclusions for auto-downloads. `LibraryManga`/`MangaMapper` carry bulk missing/unavailable counts, with no per-library-title queries. `48.sqm` preserves data and updates views; Main reserved 48 here and will renumber prune's independent EH-table drops to 49 at integration. Migration APKs are emulator-only until the final integrated phone build.

Theme bridge (required so leftover Material screens are neutral): `presentation/theme/TachiyomiTheme.kt`, `eu/kanade/domain/ui/UiPreferences.kt` (`appTheme` default `DEFAULT`, `themeCoverBased` default false). Reader engine files restored to pristine (`ui/reader/viewer/**`, `setting/ReadingMode.kt`, `exh/ui/metadata/MetadataViewScreen.kt`): the seed-colour path stays dormant behind the false default.

### (d) Deletions (updater / telemetry)

`data/updater/**` (5 files), `domain/release/**` (4 + test), `core/common/…/security/PrivacyPreferences.kt`, `telemetry/**` (7), `mihon/core/migration/migrations/SetupAppUpdateMigration.kt`, updater UI: `presentation/more/{ComingUpdatesScreen, NewUpdateScreen, WhatsNewScreen}.kt`, `ui/more/{ComingUpdatesScreen, NewUpdateScreen, WhatsNewScreen}.kt`, `presentation/more/settings/screen/about/WhatsNewDialog.kt`.

Docs/tests/other (ours, no upstream counterpart): `app/src/test/**`, `app/src/main/res/drawable/ic_tsuzuki.xml`, `tsuzuki.png`.

### (e) Deletions (Decision 4 feature prune, `feat/prune`)

Commits `379e42d` (Discord RPC / Connections) and `e6bac62` (page previews):
`data/connections/**`, `domain/connections/**`, `ui/setting/connections/**`,
`presentation/connection/**`, Connection settings screens + widget + preference
item (ours + upstream twins), `exh/pagepreview/**`, `GetPagePreviews`,
domain `PagePreview`, `PagePreviewCache`, coil `PagePreviewFetcher/Keyer`,
`presentation/manga/components/PagePreviews.kt`,
`ClearBrokenPagePreviewCacheMigration`. Manifest: DiscordRPCService service.
`source-api PagePreviewSource.kt` deliberately kept until the NHentai/Lanraragi
delegate deletion (they still implement it).

| Feature family | Deleted upstream paths | Retained compatibility |
|---|---|---|
| E-Hentai/ExHentai core | `exh/eh/**`, `exh/uconfig/**`, EH source/metadata, login/settings/configuration UI, EH description adapter and logo, EH-only utility helpers and startup migrations | `EXHMigrations` and source-id constants; MangaDex assets and delegate plumbing |
| Gallery pipeline | `exh/favorites/**`, `GalleryAdder`, batch-add UI, favorites domain/repository/schema, favorites dialogs and EH paging | MangaDex-only `InterceptActivity`; SQLDelight `48.sqm` drops legacy favorites tables |
| NSFW delegates | NHentai/Pururin/EightMuses/Lanraragi sources, metadata and description adapters; DelegateNHentaiMigration; source-api `PagePreviewSource` | MangaDex delegate/metadata/description/viewer; old source-id backup migration and external extension support |

## Hook points (final table)

Upstream files that differ from `vendor/komikku` for UI routing. Function = where the delegation happens; reason = what it buys.

| File | Function | Reason |
|---|---|---|
| `eu/kanade/tachiyomi/ui/main/MainActivity.kt` | `setContent` | hosts `TsuzukiShell` + our `AppStateBanners`/`LocalAppStateBottomPadding`; ConfigureExhDialog; B27: hosts `PairPromptHost` (pair confirmation sheet) |
| `eu/kanade/tachiyomi/ui/main/MainActivity.kt` | navigator transition | Round 4: delegates to owned compositor-only 350ms transition, disabling size animation and exposing a glass/deferred-content motion phase |
| `eu/kanade/tachiyomi/ui/home/HomeScreen.kt` | tab composition provider | Round 4: owned tab-settle phase temporarily uses frosted tint instead of live blur during first composition and selection animation |
| `eu/kanade/tachiyomi/ui/main/MainActivity.kt` | `handleIntentAction` | early delegation to owned MangaDex settings/entity link router; B27: `tsuzuki://pair` VIEW links delegate to `PairDeepLink`; upstream intent cases remain unchanged |
| `app/src/main/AndroidManifest.xml` | MainActivity `intent-filter`s | B27 registration: `tsuzuki://pair` VIEW/BROWSABLE filter so the phone-camera QR scan of the Windows hub opens the pair confirmation |
| `eu/kanade/tachiyomi/data/sync/SyncDataJob.kt` | `doWork` catch | B27 required logic: automatic runs skip the error notification when the hub is unreachable (`SyncFailurePolicy`); manual runs still notify |
| `eu/kanade/tachiyomi/data/sync/SyncDataJob.kt` | `setupTask`, `startNow` | B30 required logic: the periodic job and the change-push job use separate unique names (`:periodic`, `:push`; the old shared `:auto` name made every trigger a KEEP no-op), automatic runs require a network, change pushes coalesce for 3 s and app open/resume syncs wait 5 s so the UI loads first; periodic floor 15 min |
| `App.kt` | `onCreate`, `onStart` sync triggers | B30 required logic: start/resume syncs pass `SyncDataJob.OPEN_DELAY_SECONDS` |
| `eu/kanade/tachiyomi/data/sync/service/SyncYomiSyncService.kt` | `doSync` catch | B27 required logic: transport failures no longer notify at the service; the worker owns the manual/auto notification decision (protocol errors still surface) |
| `eu/kanade/tachiyomi/data/sync/service/SyncYomiSyncService.kt` | `companion object` client | B27 required logic: full syncs to the PC hub upload/render ~100k chapters, so the default 10 s OkHttp read timeout aborts mid-sync; connect 10 s, read/write 120 s |
| `data/…/chapters.sq` | `insert` | B28 required logic: chapter INSERT stamps `last_modified_at = strftime('%s','now')` (epoch seconds, matching the update trigger) so refresh-added chapters ride the sync delta watermark instead of waiting for the 24 h full; docs/research/desktop-v2/04-sync-architecture.md §4.2.5 |
| `eu/kanade/tachiyomi/data/sync/service/SyncYomiSyncService.kt` | `probeHubVersion` | B28 required logic: R2 cheap poll `GET /api/sync/v2/version`; null on hubs without the endpoint so syncs fall back to the full flow. B29: strictly capped (5 s) — a hub that accepts and never answers must not hold the pipeline for the client's 120 s read timeout; on timeout returns null and the caller merges normally |
| `eu/kanade/tachiyomi/data/sync/SyncManager.kt` | `syncData` guard, `localSyncWork` | B28 required logic: R2 idle skip — when the hub seq equals the cursor and nothing is pending locally (watermark stamps, tombstones, full due, section digest change), skip the backup build and merge request. B29: delta syncs build only delta candidates (isDeltaCandidate stamp filter), so a hub change no longer waits on a full-library backup before the merge request |
| `data/…/mangas.sq` | `hasSyncStampSince` | B28 required logic: watermark stamp probe feeding the R2 idle skip |
| `data/…/chapters.sq` | `hasSyncStampSince`, `mangaIdsWithChapterStampSince`, `idx_chapters_last_modified_at` | B28 required logic: watermark stamp probe feeding the R2 idle skip. B29: stamp index (migration `50.sqm`) so the pre-merge local check is index-backed instead of scanning all chapters. B30: one index-backed query returns every manga with a chapter stamp, replacing a per-manga probe (~1,700 queries per sync) in the delta candidate filter |
| `eu/kanade/tachiyomi/data/sync/SyncDelta.kt` | `selectSyncSections` digest set, `syncIsSkippable` | B28 required logic: categories and wanted extensions join the section digests so the idle skip cannot strand their changes; skip decision function. B29: `isDeltaCandidate` — stamp-based delta build predicate (manga row stamp or any chapter stamp) |
| `eu/kanade/tachiyomi/data/sync/SyncDelta.kt` | `selectSyncDelta` pushed guard, `PushedManga` codec | B28 required logic: R3 acked-version guard — manga whose version, progress-chapter versions and chapter count all match the last acked snapshot are metadata-only churn and ride the 24 h full |
| `eu/kanade/tachiyomi/data/sync/SyncManager.kt` | `acknowledgeResult` snapshot | B28 required logic: ack stores per-manga pushed state from the pre-trim snapshot feeding the R3 guard |
| `eu/kanade/domain/sync/SyncPreferences.kt` | `pushedManga` | B28 required logic: R3 acked pushed-state storage (appState, wiped with the sync identity) |
| `eu/kanade/tachiyomi/data/sync/service/SyncYomiSyncService.kt` | `requireV2Capabilities` caps cache, `X-Sync-Caps` request header | B28 required logic: R1 — phone advertises chapter-delta only when the hub's cached capabilities listed it; empty cache re-probes so hub upgrades are picked up |
| `eu/kanade/tachiyomi/data/sync/{SyncDelta, service/SyncYomiSyncService}.kt`, `eu/kanade/domain/sync/SyncPreferences.kt` | `shouldReprobeCapabilities`, `requireV2Capabilities(force)`, `capabilitiesProbedAt` | B33 required logic: cached hub capabilities are re-probed after 24 h or when a merge reports a hub seq jump of 1000 or more, so a hub upgrade's new capabilities reach phones without a reinstall |
| `eu/kanade/tachiyomi/data/sync/SyncManager.kt` | `applyRemoteBackup` capped guard | B28 required logic: R4 — a capped-quiet delta omits categories and must not run the absence-driven category delete path |
| `eu/kanade/tachiyomi/ui/home/HomeScreen.kt` | `Content` | shell host: glass capsule tab bar, `LocalShell*` providers, badge counts |
| `eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` | `setComposeOverlay` + `AppBars` | our reader chrome (`ui.reader.ReaderAppBars`, settings dialog, mode/orientation dialogs, glass context, contrast-aware system bars); also carries the deliberate swipe fix (see Local deviations) and `flushReaderState` sync trigger |
| `eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` | `ChapterListDialog` import | Round 3: our titled Chapters detent and grouped current-chapter list; native reader View hierarchy and engine unchanged |
| `eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` | overlay state subscription / cover theme | Round 4: owned allocation-free hidden-chrome flow gate; page indicator alone observes page state; system-bar styling runs only when canvas theme changes. Disabled cover theming creates no collector; enabled cover theming observes distinct manga snapshots only |
| `ui/manga/MangaScreen.kt`, `MangaScreenModel.kt` | prepared state / chapter comparator | Round 4: one-call owned background preparation warms immutable chapter filtering/sorting/separators before publishing to Compose; resolve chapter comparator once per sort rather than once per comparison |
| `exh/md/handlers/{ApiMangaParser,MangaDexFeatureMapper,MangaDexStatistics}.kt` | missing chapter estimate | Round 4: delegate numbering credibility to owned bounded helper; represent unavailable live statistics as null so Details omits unreliable estimates; legacy integer metadata retains zero for unknown |
| `mihon/feature/upcoming/UpcomingScreen.kt` | `UpcomingScreenContent` call | Round 3: one-call delegation to our calendar and grouped release list; upstream ScreenModel and release prediction untouched |
| `eu/kanade/tachiyomi/ui/home/HomeScreen.kt` | `TabIcon` icon call | Round 3: one-call outline tab symbol renderer; existing update-count collection and badges remain upstream |
| `ui/library/LibraryTab.kt` | `Content` | `TsuzukiLibraryScreen` + library dialogs + our `ChangeCategoryDialog` |
| `ui/updates/UpdatesTab.kt` | `Content` | our `UpdateScreen` + filters/confirm dialogs |
| `ui/history/HistoryTab.kt` | `Content` | our `HistoryScreen` + history dialogs + our `ChangeCategoryDialog` |
| `ui/browse/BrowseTab.kt` | `Content` | our `BrowseTabContent` (switches between HYBRID, DISCOVER, and CLASSIC) |
| `ui/more/MoreTab.kt` | `Content` | our `MoreScreen` |
| `ui/browse/source/SourcesTab.kt` | `sourcesTab` | our `SourcesScreen` composable; our `TabContent` |
| `ui/browse/extension/ExtensionsTab.kt` | `extensionsTab` | our `ExtensionScreen`; our `TabContent` |
| `ui/feed/FeedTab.kt` | `Content` | our `dev.errnolink.tsuzuki.ui.feed.FeedTabContent()` (layout lab switch between UnifiedFeedScreen and ClassicFeedScreen) |
| `ui/browse/source/SourcesScreen.kt` | `Content` | our `BrowseTabWrapper` |
| `ui/browse/source/browse/BrowseSourceScreen.kt` | `Content` | our `BrowseSourceContent` + our `ChangeCategoryDialog` |
| `ui/browse/source/browse/SourceFilterDialog.kt` | dialog body | our `ShellFilterRow` + settings sheets |
| `ui/browse/source/feed/SourceFeedScreen.kt` | `Content` | our `SourceFeedScreen` |
| `ui/browse/source/feed/SourceFeedScreen.kt` | feed order crossfade | B34: the sort-feed crossfade target is our `SourceFeedOrderContent` (Tsuzuki reorder list) |
| `ui/browse/source/globalsearch/GlobalSearchScreen.kt` | `Content` | our `GlobalSearchScreen` |
| `ui/browse/extension/ExtensionsScreen.kt` | `Content` | our `ExtensionScreen` |
| `ui/browse/migration/search/MigrateSourceSearchScreen.kt` | `Content` | our `BrowseSourceContent` for migration search; B34 legacy sweep: the Material `Scaffold`/`SearchToolbar`/bulk toolbar swapped for `ShellContentScaffold` glass controls with the filter action |
| `ui/browse/extension/ExtensionFilterScreen.kt` | `Content` | B34 legacy sweep: our `ExtensionFilterContent` (language filter on the Tsuzuki shell) |
| `ui/browse/extension/details/ExtensionDetailsScreen.kt` | `Content` | B34 legacy sweep: our `ExtensionDetailsContent` (glass top controls, Tsuzuki toggles) |
| `ui/browse/extension/details/SourcePreferencesScreen.kt` | `Content` | B34 legacy sweep: our `SourcePreferencesContent`; the androidx `PreferenceFragmentCompat` host and fragment are retired and the `PreferenceScreen` renders as Tsuzuki rows |
| `ui/browse/source/SourcesFilterScreen.kt` | `Content` | B34 legacy sweep: our `SourcesFilterContent` (grouped language/source toggles) |
| `presentation/webview/WebViewScreenContent.kt` | top chrome | B34 legacy sweep: Material `Scaffold`/`AppBar` swapped for `DeclareShellTop` glass controls; WebView windows, popup stack and loading states unchanged |
| `presentation-core/…/screens/EmptyScreen.kt` | actions layout | B34: one primary pill action, remaining actions as a spaced text row, instead of the stretched button row (issue 1 empty-state fix for remaining Material screens) |
| `ui/manga/MangaScreen.kt` | `Content` | our details UI (`ui.details.MangaScreen`), chapter settings dialog, cover content; continue-chapter history subscription scoped to the opened manga and its merged children |
| `data/…/view/historyView.sq` + `migrations/52.sqm`, `HistoryRepository(Impl)`, `GetHistory` | `historyView`, `getRecentHistory`, `getLatestHistoryByMangaIds` | B31 required logic: the view aggregates chapter counts only for manga that have history and drives max-read from the history table (was four full chapter scans per query, re-run on every chapters/history write); the Continue shelf and Feed Up Next take a SQL `LIMIT`, Details reads only its own manga ids |
| `ui/manga/track/TrackInfoDialog.kt` | selector/remover/search screen `Content` | Round 5: the seven tracker dialog screens delegate to owned glass sheets in `dev.errnolink.tsuzuki.ui.manga.track` (Models moved with them) |
| `ui/manga/PaletteScreen.kt` | `Content` | Round 5: dormant palette screen delegates to the owned glass token swatch screen; the material seed-colour preview is retired |
| `ui/category/CategoryScreen.kt` | `Content` | our category screen + dialogs (import swap) |
| `ui/category/genre/SortTagScreen.kt` | `Content` | our tag sort list + dialogs (import swap) |
| `ui/category/sources/SourceCategoryScreen.kt` | `Content` | our source category list + dialogs (import swap) |
| `ui/download/DownloadQueueScreen.kt` | `Content` | delegates to `ui.downloads.DownloadQueueScreen.Content()` |
| `ui/more/OnboardingScreen.kt` | `Content` | our onboarding steps |
| `ui/stats/StatsScreen.kt` | `Content` | our stats sections + `SettingsScaffold` |
| `presentation/more/settings/screen/SearchableSettings.kt` | `PreferenceScaffold` usage | our settings scaffold keeps pristine widget signatures compiling |
| `presentation/more/settings/screen/{SettingsMainScreen, SettingsAppearanceScreen, SettingsConnectionScreen, SettingsDataScreen, SettingsDiscordScreen, SettingsDiscordAccountsScreen, SettingsEhScreen, SettingsMangadexScreen, SettingsSearchScreen, SettingsSecurityScreen, SettingsTrackingScreen, SettingsAdvancedScreen}.kt` | `Content` | `Content()` delegates to the mirrored `Tsuzuki…` object in `ui.settings` |
| `presentation/more/settings/screen/about/AboutScreen.kt` | `Content` | `TsuzukiAboutScreen` (restored from `b4c0c05` rebrand base) |
| `presentation/more/settings/screen/about/OpenSourceLicensesScreen.kt` | `Content` | `TsuzukiOpenSourceLicensesScreen` (grouped licence list) |
| `presentation/more/settings/screen/about/OpenSourceLibraryLicenseScreen.kt` | `Content` | `TsuzukiOpenSourceLibraryLicenseScreen` (monospace licence text) |
| `presentation/more/settings/screen/debug/BackupSchemaScreen.kt` | `Content` | `TsuzukiBackupSchemaScreen` (monospace proto schema in `ui.settings.debug`) |
| `presentation/more/settings/screen/debug/WorkerInfoScreen.kt` | `Content` | `TsuzukiWorkerInfoScreen` (grouped worker sections in `ui.settings.debug`) |
| `presentation/more/settings/screen/advanced/ClearDatabaseScreen.kt` | `Content` | `TsuzukiClearDatabaseScreen` |
| `presentation/more/settings/screen/appearance/AppLanguageScreen.kt` | `Content` | `TsuzukiAppLanguageScreen` |
| `presentation/more/settings/screen/browse/ExtensionStoresScreen.kt` | `Content` | our `ExtensionStoresScreen` composable + dialogs |
| `presentation/more/settings/screen/data/{CreateBackupScreen, RestoreBackupScreen, SyncSettingsSelector, SyncTriggerOptionsScreen}.kt` | `Content` | `Tsuzuki…` screens |
| `presentation/theme/TachiyomiTheme.kt` | theme bridge | maps Material tokens onto Tsuzuki tokens so leftover pristine screens render neutral |
| `eu/kanade/domain/ui/UiPreferences.kt` | theme prefs | prune §7: deleted dead appTheme, colorTheme, customThemeStyle, themeCoverBased, preloadLibraryColor |
| `data/…/DatabaseHandler.kt`, `AndroidDatabaseHandler.kt`, `manga/MangaRepositoryImpl.kt` | library subscription | required logic: conflate/debounce invalidations before executing SQL, immediate first result, suppress equal library results |
| `domain/…/MangaMergeRepository.kt`, `data/…/MangaMergeRepositoryImpl.kt` | `getMergedMangaForLibrary` | required logic: bulk merged-child snapshot replaces per-library-item SQL |
| `data/download/DownloadCache.kt`, `ui/library/{LibraryScreenModel,LibraryItem}.kt` | library mapping/filtering | required logic: lifecycle/restore/sync collection gate (`dev.errnolink.tsuzuki.data.library.libraryCollectionActive`: the first load always runs, a loaded library holds still during a sync or restore and refreshes after), background mapping, batched download counts shared by badges and filters |
| `ui/library/LibraryScreenModel.kt` (continue targets), `dev.errnolink.tsuzuki.data.library.ContinueTargetsResolver`, `data/…/tsuzuki_continue_targets.sq` | continue-target resolution | required logic: batched next-unread resolution for tile continue buttons — two chunked queries per recompute regardless of library size (was two queries per candidate), recomputed only when candidate ids, manga flags/versions/titles, download counts, unread-row aggregates or excluded scanlators change; the upstream `getNextUnread` selection is reused unchanged. Merged favorites (`source == MERGED_SOURCE_ID`) resolve through their merged chapters on every emission; the old check compared the manga id with the merged source id, so merged titles never got a continue target |
| `data/…/search_tags.sq`, `data/…/search_titles.sq`, `MangaMetadataRepository(Impl)`, `GetSearchTags`, `GetSearchTitles`, `GetIdsOfFavoriteMangaWithMetadata`, `ui/library/LibraryScreenModel.kt` (`filterLibrary`) | library search | required logic: reactive metadata index subscription shared via flow instead of re-querying tags/titles/tracks from DB on every search keystroke |
| `ui/manga/MangaScreenModel.kt` | constructor initial state | `DetailsEarlyState` reads the manga row synchronously so Details composes its header on the first frame; chapters arrive with `chaptersPending` |
| `data/BannerProgressStatus.kt`, `data/backup/BackupNotifier.kt` | active status / progress publishing | required logic: immediate bulk-operation state for the library gate, restore notifications limited to one per 250 ms |
| `data/coil/{MangaCoverFetcher,MangaCoverMetadata}.kt`, `ui/main/MainActivity.kt`, `domain/…/MangaCover.kt` | cover metadata / pause | required logic: bounds-only ratio extraction on the fetch dispatcher, compact incremental SQLite cache, no palette preloading or full preference rewrites on pause; obsolete metadata comments removed |
| `App.kt` | `onCreate`, `onStart` | required logic: minimal immediate log sink, background full XLog/storage setup and cover-cache warmup; widgets and start/resume sync move off-main after migration readiness; WorkManager stays initialized before migration consumers |
| `app/src/main/baseline-prof.txt` | Tsuzuki hot-path rules | build/profile: inherited Komikku profile now includes migrated library/shell/details/reader and design-system class/method rules; phone-generated refinement remains deferred |
| `ui/setting/track/{BaseOAuthLoginActivity,TrackLoginActivity}.kt` | callback / task return | required logic: reject foreign/expired/replayed state, bound sign-in to 20 seconds, report failures, finish and reuse MainActivity with NEW_TASK/CLEAR_TOP/SINGLE_TOP |
| `data/track/{anilist/AnilistApi,bangumi/BangumiApi,shikimori/ShikimoriApi,myanimelist/MyAnimeListApi}.kt` | authorization URL / token exchange | required logic: persist per-flow OAuth state; MAL verifier generated for each flow, persisted with it and explicitly supplied to exchange (plain PKCE, as required by MAL) |
| `data/track/{anilist/Anilist,bangumi/Bangumi,shikimori/Shikimori,myanimelist/MyAnimeList}.kt` | login | required logic: propagate login errors/cancellation after clearing failed credentials rather than swallowing them |
| `util/CrashLogUtil.kt` | construction / `getExtensionsInfo` | required logic: resolve ExtensionManager only when an actual extension diagnostic dump is requested, never for ordinary boot/device information |
| `buildSrc/…/ProjectExtensions.kt` | `configureCompose` | build: share an exact-type stability allowlist for Manga, Chapter, LibraryManga, Category and LibraryItem snapshots; no package-wide mutable-model exemption |
| `ui/reader/viewer/ReaderPageImageView.kt` | `setImage`, `recycle`, Coil enqueue | required logic: retain both static/webtoon and animated lambda-target Coil Disposables; cancel previous work on rebind and recycle before releasing/hiding page views |
| `app/proguard-rules.pro` | kotlinx serialization rules | build: preserve Tsuzuki serializable DTOs, generated serializers and companion serializer entry points, mirroring existing upstream package protections without keeping every UI class/member |
| `buildSrc/src/main/kotlin/mihon/buildlogic/tasks/LocalesConfigTask.kt` | `getLocalesConfigTask` | required build logic: execute locale resource generation after clean, not during configuration |
| `data/backup/models/Backup.kt` | field 700 | required logic: nullable `wantedExtensions` section (`dev.errnolink.tsuzuki.sync.BackupWantedExtensions`), docs/research/23-extension-sync-contract.md |
| `data/backup/create/BackupCreator.kt` | `backup` | appends the wanted-extensions section to file backups (app-settings option + sync-extensions pref) |
| `data/sync/SyncManager.kt` | `syncData` | appends the wanted-extensions section to the SyncYomi v2 payload |
| `data/backup/restore/BackupRestorer.kt` | `restoreFromFile` | reconciles wanted vs installed extensions after restore/sync apply (app-settings option) |
| `data/notification/Notifications.kt` | `CHANNEL_EXT_SYNC` | quiet ext-sync progress channel + id |
| `extension/ExtensionManager.kt` | `InstallationListener` | records installs/updates and deliberate uninstalls into the ext-sync store |
| `eu/kanade/domain/sync/SyncPreferences.kt` | `syncExtensions`/`syncUninstalls` | ext-sync preferences (both default on) |
| `eu/kanade/domain/base/ExtensionInstallerPreference.kt` | `defaultValue` | default installer is `PRIVATE` for new installs (Decision 5.5) |
| `App.kt` | `onCreate` | launches the wanted-extension reconcile and updater jobs on app start |
| `data/…/chapters.sq` + `migrations/51.sqm` | `idx_chapters_is_syncing` | B31 required logic: every restored manga runs `resetIsSyncing`, which scanned the whole chapters table (46 ms on 71k chapters, per manga); a partial index on `is_syncing = 1` makes it an index probe |
| `data/…/history.sq` | `getBackupHistoryByMangaId`, `getRestoreHistoryTargets` | B31 required logic: history rows carry their chapter URL in one query per manga, replacing one or two lookups per history row in backup build and restore |
| `eu/kanade/tachiyomi/data/backup/create/creators/MangaBackupCreator.kt` | `invoke`, `backupMangasBatched` | B33 required logic: batches chapter, category, tracking, history, merged reference, and metadata queries in chunks of up to 500 manga rather than issuing 6+ queries per manga |
| `eu/kanade/tachiyomi/data/backup/restore/restorers/MangaRestorer.kt` | `restoreHistory` | B31 required logic: resolves history targets from `getRestoreHistoryTargets` once per manga instead of two queries per history row |
| `eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt` | `restore(Backup)` | B31 required logic: sync applies the decoded response without a temp-file round trip. Manga stay one transaction each: wrapping 25 in one outer transaction deadlocked the DB pool on device (nested transaction parked while another reader waited on the single connection) |
| `eu/kanade/tachiyomi/data/sync/SyncDataJob.kt` | `doWork` | B31 required logic: one sync at a time per process (mutex). The periodic and push jobs have separate unique names, so they could run two full syncs in parallel and double the build/apply cost |
| `exh/md/utils/MdExtensions.kt` | `mdListCall` | B31 required logic: fetches page one, then every remaining offset in parallel (was one page after another), so a never-opened MangaDex title's chapter list arrives in about one round trip instead of one per page |
| `eu/kanade/tachiyomi/data/sync/SyncManager.kt` | `applyRemoteBackup` | B31 required logic: restores `result.backup` in memory through `BackupRestorer.restore(Backup)`. 0.1.0: the response first passes `dev.errnolink.tsuzuki.sync.UnchangedRemoteManga.drop` against the backup this sync built, so manga the hub returns unchanged (chapter order and `lastModifiedAt` aside) are not restored again; a full sync re-restored all 1,422 emulator titles for ~5 minutes when 6 had changed |
| `eu/kanade/tachiyomi/data/sync/{SyncManager, service/SyncService, service/SyncYomiSyncService}.kt` | `syncData` identity, `SyncData.identity`/`fullUpload`, `doSync` | B31 required logic: one sync uses one pairing identity and one full/delta decision. Pairing mid-sync used to reset the cursor between the delta build and the merge, so a delta went up flagged full, the ack stamped `lastFullSync`, and the hub stayed missing most of the library until the next 24 h full (seen on the emulator: 318 of 1,409 titles). Now `doSync` fails on an identity change and the ack is skipped |
| `App.kt` | `onCreate` observer | B31 required logic: registers `dev.errnolink.tsuzuki.sync.HubLiveSync`; while the app is in the foreground it long-polls the hub's `GET /api/sync/v2/version?since=<cursor>&wait=25` (`version-wait` capability) and starts a sync when the hub moves, so PC changes reach an open phone in about a second |
| `eu/kanade/tachiyomi/source/online/all/MangaDex.kt` | `baseHttpClient`, `mangadexService` client | B33 required logic: both MangaDex OkHttp clients route through the shared `MangaDexRateLimit` interceptor (5 req/s to api.mangadex.org, user/covers/background priorities; Neko C2 port), so every MangaDex API call, including the parallel `mdListCall` pages, queues through one limiter |
| `eu/kanade/tachiyomi/source/online/all/MangaDex.kt` | `getMangaUpdate` | B34 required logic: details and chapter list are fetched concurrently (as `CatalogueSource.getMangaUpdate` does for other sources), so a never-opened title lists chapters without waiting for the details round trips |
| `exh/md/similar/MangaDexSimilarPagingSource.kt` | `client` | B33 required logic: similar/related requests ride the shared MangaDex rate limiter instead of the bare global client |
| `eu/kanade/tachiyomi/data/backup/BackupDecoder.kt` | `decode`, `decodeMetadata`, `decodeEntries` | B33 required logic: stream backup decode and metadata count (Mihon commit `771ba33a`) |
| `eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt` | `createBackup` | B33 required logic: stream backup encoding through BackupWriter without buffering all entries in memory |
| `eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt` | `restore`, `restoreManga` | B33 required logic: restore library entries in streaming batches of 100 from BackupDecoder.decodeEntries |
| `ui/browse/migration/search/MigrateSearchScreen.kt` | `Content` | B34 legacy sweep: delegates to the Tsuzuki `GlobalSearchScreen`, made fromSourceId-aware (the migrating-from source is marked with ▶) |
| `presentation/manga/MangaNotesScreen.kt` | chrome | B34 legacy sweep: Material `Scaffold`/`AppBar` swapped for `ShellContentScaffold` (title + manga-title caption); the rich-text area keeps its upstream behaviour |
| `presentation/libraryUpdateError/LibraryUpdateErrorScreen.kt` | chrome | B34 legacy sweep: Material app bar/action mode replaced by `SettingsScaffold` glass controls; the scroll/migrate bottom bar keeps its confirm buttons on a grouped background; rows and swipe-to-delete unchanged |
| `exh/recs/components/RecommendsScreen.kt` | chrome | B34 legacy sweep: Material `Scaffold`/`AppBar` swapped for `ShellContentScaffold`; recommendation sections render as Tsuzuki cover-card rows with the shared skeleton and error text |
| `exh/recs/BrowseRecommendsScreen.kt` | `Content` | B34 legacy sweep: Material simple toolbar/bulk toolbar swapped for `ShellContentScaffold` glass controls; display mode is a top action with the shared choice sheet; the Tsuzuki `BrowseSourceContent` replaces the Material one |
| `exh/ui/smartsearch/SmartSearchScreen.kt` | chrome | B34 legacy sweep: Material `Scaffold`/`AppBar` swapped for `ShellContentScaffold`; the searching spinner and the auto-navigation to the merge target keep their upstream behaviour |
| `exh/md/follows/MangaDexFollowsScreen.kt` | `Content` | B34 legacy sweep: Material simple toolbar/bulk toolbar swapped for `ShellContentScaffold` glass controls; display mode is a top action with the shared choice sheet; the Tsuzuki `BrowseSourceContent` replaces the Material one |
| `exh/ui/metadata/MetadataViewScreen.kt` | chrome | B34 legacy sweep: Material `Scaffold`/`AppBar` and the theme wrapper replaced by `ShellContentScaffold`; metadata pairs render as Tsuzuki two-line rows keeping long-press copy |
| `presentation/category/BiometricTimesScreen.kt` (+ list item) | chrome | B34 legacy sweep: Material `Scaffold`/`AppBar`/FAB replaced by `SettingsScaffold` with an Add top action; time ranges render as grouped Tsuzuki rows with a delete action; the system time picker and delete dialog keep their upstream behaviour |
| `exh/debug/SettingsDebugScreen.kt` | chrome | B34 legacy sweep: Material `Scaffold`/`AppBar`, `TextPreferenceWidget` rows and the result `AlertDialog` replaced by `SettingsScaffold`, grouped rows and a `DetentSheet`; the reflective debug functions/toggles keep their behaviour and a back button was added |
| `ui/deeplink/DeepLinkScreen.kt` | chrome | B34 legacy sweep: the Material `Scaffold`/`AppBar` around the loading state swapped for `ShellContentScaffold`; resolution still replaces to the manga/reader/global search without composing more chrome |

Not routed (still upstream Material, listed by `tools/check-routes.py`): none — every Voyager screen/tab found under `eu/kanade`/`exh` carries a Tsuzuki delegation marker; `tools/check-routes.py` reports 64/64 routed after the B34 legacy sweep.

## Importing a Komikku release

1. Download the new release source zip; on `vendor/komikku` replace the whole tree (keep `.git`), commit `vendor: Komikku vX.Y.Z`.
2. Full import: on the product branch run `git merge vendor/komikku`. Conflicts are expected only at the hook points above, in the required-logic files of §(c), and in deleted files (resolve deleted as "keep deleted"). At hook conflicts keep our delegation lines and re-check the new upstream body for anything our composable must consume.
3. Partial import (single feature/fix): `git diff <old-vendor-tag> vendor/komikku -- <paths> > feature.patch` on the vendor branch, then `git apply -3 feature.patch` (or `git am -3` for commits cherry-picked off the vendor branch) on the product branch. `-3` uses our pristine-restored files as the merge base, so patches that only touch upstream code apply cleanly; patches touching hooks/required logic stop exactly at the lines listed here.
4. After either path: map any new upstream `Screen` or `Tab` in `Routes.kt` (style it in `ui.*`, or leave it on the neutral bridge), then run `:app:compileDebugKotlin` and `:app:testDebugUnitTest`.
5. Record the new version in this file's table and any new hooks/required-logic in the same commit. A file adapted from Neko gets the first-line Neko notice.

## Round 3 glass capture hook correction

`HomeScreen.Content` no longer adds `glassSource` to the ancestor wrapping all tab chrome. Each owned list/masonry/content scaffold registers only its scrolling content, reusing `LocalShellGlass`. This leaves one active capture per screen and lets both top groups and the tab capsule sample it. The first round-3 emulator scroll capture showed why ancestor capture is invalid: top effects inside the captured subtree were transparent over unblurred covers. No source/data/navigation logic changes.
