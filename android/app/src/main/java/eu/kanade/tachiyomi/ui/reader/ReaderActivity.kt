package eu.kanade.tachiyomi.ui.reader

import android.Manifest
import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.ui.details.DetailDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.hippo.unifile.UniFile
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.readingMode
import dev.errnolink.tsuzuki.reader.readerCanvasArgb
import dev.errnolink.tsuzuki.reader.readerCanvasColor
import dev.errnolink.tsuzuki.reader.readerCanvasDark
import eu.kanade.domain.ui.UiPreferences
import dev.errnolink.tsuzuki.ui.reader.ChapterListDialog
import eu.kanade.presentation.reader.DisplayRefreshHost
import dev.errnolink.tsuzuki.ui.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import dev.errnolink.tsuzuki.ui.reader.ReaderLoadingSurface
import dev.errnolink.tsuzuki.ui.reader.readerChromeState
import dev.errnolink.tsuzuki.ui.reader.ReadingModeSelectDialog
import dev.errnolink.tsuzuki.ui.reader.NavBarType
import dev.errnolink.tsuzuki.ui.reader.ReaderAppBars
import dev.errnolink.tsuzuki.ui.reader.formatChapterShortName
import tachiyomi.presentation.core.i18n.stringResource
import dev.errnolink.tsuzuki.ui.reader.ReaderSettingsDialog
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import exh.util.defaultReaderType
import exh.util.mangaType
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.UrlUtils
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds
/** Mirrors TsuzukiTheme's system reduced-motion policy (animator or transition scale disabled). */
private fun Context.animationsReduced(): Boolean =
    animatorDurationScale == 0f ||
        Settings.Global.getFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f

class ReaderActivity : BaseActivity() {

    companion object {

        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?/* SY --> */, page: Int? = null/* SY <-- */): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                // SY -->
                putExtra("page", page)
                // SY <--
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private val readerPreferences = Injekt.get<ReaderPreferences>()
    private val preferences = Injekt.get<BasePreferences>()

    private var composeReaderHost: dev.errnolink.tsuzuki.ui.reader.ComposeReaderHost? = null

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModels<ReaderViewModel>()
    private var assistUrl: String? = null

    // SY -->
    private val sourceManager = Injekt.get<SourceManager>()
    // SY <--

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private val readerNotices = MutableStateFlow<dev.errnolink.tsuzuki.ui.reader.ReaderNotice?>(null)
    private var noticeSeq = 0L
    private val displayRefreshHost = DisplayRefreshHost()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, window.decorView) }

    private var readerStage: dev.errnolink.tsuzuki.reader.ReaderTransition.Stage? = null

    /**
     * Back is the open in reverse: the predictive gesture scrubs the close flight (the reader
     * shrinking into its origin with the title page behind), release springs it back, commit
     * continues from the released pose (T6). Without a stage the default back applies.
     */
    private val readerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            val stage = readerStage
            if (stage == null || !stage.canExit()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
                return
            }
            stage.beginScrub()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            readerStage?.scrubTo(dev.errnolink.tsuzuki.reader.readerBackPose(backEvent.progress))
        }

        override fun handleOnBackCancelled() {
            readerStage?.releaseScrub()
        }

        override fun handleOnBackPressed() {
            finish()
        }
    }

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        readerStage = dev.errnolink.tsuzuki.reader.ReaderTransition.take(this)
        readerBackCallback.isEnabled = readerStage != null
        onBackPressedDispatcher.addCallback(this, readerBackCallback)
        val reduced = animationsReduced() || readerStage != null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                if (reduced) 0 else R.anim.reader_open_enter,
                if (reduced) 0 else R.anim.reader_open_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                if (reduced) 0 else R.anim.reader_open_enter,
                if (reduced) 0 else R.anim.reader_open_exit,
            )
        }

        // The system-bar state is decided once, before the transition starts: the chrome opens
        // with the reader, so the bars stay up from the first frame and the style follows the
        // canvas the page will sit on (T4).
        val canvasStyle = if (readerCanvasDark(readerPreferences.readerTheme().get(), baseContext.isNightMode())) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = canvasStyle, navigationBarStyle = canvasStyle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.setComposeOverlay()
        readerStage?.enter(binding.root)

        if (viewModel.needsInit()) {
            val manga = intent.extras?.getLong("manga", -1) ?: -1L
            val chapter = intent.extras?.getLong("chapter", -1) ?: -1L
            // SY -->
            val page = intent.extras?.getInt("page", -1).takeUnless { it == -1 }
            // SY <--
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(this, manga.hashCode(), Notifications.ID_NEW_CHAPTERS)

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter/* SY --> */, page/* SY <-- */)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown err")
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        config = ReaderConfig()
        // The chrome opens with the reader (the controls appear with the page, never after it);
        // a tap then hides it for immersive reading.
        setMenuVisibility(true)

        // EXH -->
        enableExhAutoScroll()
        // EXH <--

        // Finish when incognito mode is disabled
        preferences.incognitoMode().changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderViewModel.Event.PageChanged -> {
                        displayRefreshHost.flash()
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page /* SY --> */, event.secondPage /* SY <-- */)
                    }
                    is ReaderViewModel.Event.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun ReaderActivityBinding.setComposeOverlay(): Unit = composeOverlay.setComposeContent {
        // KMK -->
        TachiyomiTheme {
            androidx.compose.runtime.CompositionLocalProvider(
                dev.errnolink.tsuzuki.designsystem.LocalWindowWidthClass provides dev.errnolink.tsuzuki.designsystem.rememberWindowWidthClass(),
            ) {
            val glass = rememberGlassContext()
            val readerBackground by readerPreferences.readerTheme().collectAsState()
            val darkCanvas = readerCanvasDark(readerBackground, androidx.compose.foundation.isSystemInDarkTheme())
            LaunchedEffect(darkCanvas) {
                val style = if (darkCanvas) SystemBarStyle.dark(Color.TRANSPARENT) else
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
            }
            val context = LocalContext.current
            // KMK <--
            val state by remember { viewModel.state.readerChromeState() }
                .collectAsState(viewModel.state.value)
            val viewerChapters by remember {
                viewModel.state.map { it.viewerChapters }.distinctUntilChanged()
            }.collectAsState(initial = viewModel.state.value.viewerChapters)
            val currentChapter = viewerChapters?.currChapter
            val currentChapterState by remember(currentChapter) {
                currentChapter?.stateFlow ?: MutableStateFlow(ReaderChapter.State.Wait)
            }.collectAsState()
            val firstPage = (currentChapterState as? ReaderChapter.State.Loaded)?.pages?.let { pages ->
                pages.getOrNull(currentChapter?.requestedPage?.coerceIn(0, pages.lastIndex) ?: 0)
            }
            val firstPageStatus by remember(firstPage) {
                firstPage?.statusFlow ?: MutableStateFlow<Page.State?>(null)
            }.collectAsState()
            var opened by remember { mutableStateOf(false) }
            val firstPageSettled = firstPageStatus == Page.State.Ready || firstPageStatus is Page.State.Error
            if (!opened && (firstPageSettled || currentChapterState is ReaderChapter.State.Error)) {
                androidx.compose.runtime.SideEffect { opened = true }
            }
            val coldOpen = !opened
            val settingsScreenModel = remember {
                ReaderSettingsScreenModel(
                    readerState = viewModel.state,
                    onChangeReadingMode = viewModel::setMangaReadingMode,
                    onChangeOrientation = viewModel::setMangaOrientationType,
                )
            }

            val chromeVisible = state.menuVisible && !coldOpen
            var chromeVisibleSince by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(chromeVisible) {
                chromeVisibleSince = if (chromeVisible) chromeVisibleSince ?: System.currentTimeMillis() else null
            }
            val notice by readerNotices.collectAsState(initial = null)
            val onDismissRequest = viewModel::closeDialog

            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = coldOpen,
                    enter = androidx.compose.animation.EnterTransition.None,
                    exit = androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(
                            dev.errnolink.tsuzuki.designsystem.M3Motion.Short4,
                            delayMillis = dev.errnolink.tsuzuki.designsystem.M3Motion.Short2,
                            easing = dev.errnolink.tsuzuki.designsystem.M3Motion.Standard,
                        ),
                    ),
                ) {
                    ReaderLoadingSurface(
                        canvasColor = readerCanvasColor(
                            readerBackground,
                            androidx.compose.foundation.isSystemInDarkTheme(),
                        ),
                        dark = darkCanvas,
                        loading = coldOpen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                dev.errnolink.tsuzuki.ui.reader.ReaderPageMirror(
                    page = readerContainer,
                    glass = glass,
                    active = state.menuVisible,
                    pageKey = state.currentPage,
                    modifier = Modifier.fillMaxSize(),
                )
                ContentOverlay(state = state)

                // The chrome's scheme follows the canvas the page sits on, with the app's glass (U2),
                // at the reader's minimum tint. The reading-mode notice shares it: a small pill
                // under the top row, never over the slider and never while the controls arrive (T5).
                val morphSources = dev.errnolink.tsuzuki.ui.reader.rememberReaderMorphSources()
                androidx.compose.runtime.CompositionLocalProvider(
                    dev.errnolink.tsuzuki.ui.reader.LocalReaderMorphSources provides morphSources,
                ) {
                    dev.errnolink.tsuzuki.ui.reader.ReaderChromeTheme(canvasDark = darkCanvas) {
                        AppBars(state = state, glass = glass, chromeReady = !coldOpen)
                        dev.errnolink.tsuzuki.ui.reader.ReaderNotice(
                            notice = notice,
                            chromeVisibleSince = chromeVisibleSince,
                            glass = glass,
                            onConsumed = { readerNotices.value = null },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                                .padding(top = 8.dp + 52.dp + 10.dp),
                        )

                        var lastChapters by remember {
                            mutableStateOf<kotlinx.collections.immutable.ImmutableList<eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem>>(
                                kotlinx.collections.immutable.persistentListOf(),
                            )
                        }
                        val chaptersOpen = state.dialog is ReaderViewModel.Dialog.ChapterList
                        val loadedChapters = remember(state.dialog) {
                            if (state.dialog is ReaderViewModel.Dialog.ChapterList) {
                                viewModel.getChapters().toImmutableList().also { lastChapters = it }
                            } else {
                                null
                            }
                        }
                        var chapters by remember(loadedChapters) {
                            mutableStateOf(loadedChapters ?: lastChapters)
                        }
                        dev.errnolink.tsuzuki.ui.reader.ChapterListDialog(
                            visible = chaptersOpen,
                            onDismissRequest = onDismissRequest,
                            chapters = chapters,
                            onClickChapter = {
                                viewModel.loadNewChapterFromDialog(it)
                                onDismissRequest()
                            },
                            onBookmark = { chapter ->
                                viewModel.toggleBookmark(chapter.id, !chapter.bookmark)
                                val updated = chapters.map {
                                    if (it.chapter.id == chapter.id) {
                                        it.copy(chapter = chapter.copy(bookmark = !chapter.bookmark))
                                    } else {
                                        it
                                    }
                                }.toImmutableList()
                                chapters = updated
                                lastChapters = updated
                            },
                            dateRelativeTime = state.dateRelativeTime,
                            onDownloadAction = { chapter, action ->
                                viewModel.handleDownloadAction(chapter, action)
                            },
                        )

                        dev.errnolink.tsuzuki.ui.reader.ReaderSettingsDialog(
                            visible = state.dialog is ReaderViewModel.Dialog.Settings,
                            onDismissRequest = onDismissRequest,
                            onShowMenus = { setMenuVisibility(true) },
                            onHideMenus = { setMenuVisibility(false) },
                            screenModel = settingsScreenModel,
                        )
                    }
                }
            }
            // KMK -->
            val externalStoragePermissionNotGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_DENIED
            val permissionRequester = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = {
                    toast(KMR.strings.permission_writing_external_storage_succeed)
                },
            )
            // KMK <--

            when (state.dialog) {
                is ReaderViewModel.Dialog.Loading -> {
                    // Cold open renders the ReaderLoadingSurface instead; the dialog only
                    // covers adjacent-chapter loads while pages are already on screen.
                    if (!coldOpen) {
                        BackHandler { onDismissRequest() }
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            GlassSurface(
                                null,
                                Modifier
                                    .statusBarsPadding()
                                    .padding(top = 16.dp)
                                    .alpha(
                                        animateFloatAsState(
                                            targetValue = 1f,
                                            animationSpec = tween(160),
                                            label = "Loading HUD fade in",
                                        ).value,
                                    ),
                                shape = RoundedCornerShape(50),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    ProgressRing(0f, stringResource(MR.strings.loading), Modifier.size(18.dp))
                                    TsuzukiText(
                                        stringResource(MR.strings.loading),
                                        style = TsuzukiTheme.typography.footnote,
                                        color = TsuzukiTheme.colors.text,
                                    )
                                }
                            }
                        }
                    }
                }

                is ReaderViewModel.Dialog.Settings -> Unit

                is ReaderViewModel.Dialog.ReadingModeSelect -> {
                    ReadingModeSelectDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        onChange = { stringRes ->
                            if (!readerPreferences.showReadingMode().get()) {
                                showReaderNotice(stringRes)
                            }
                        },
                    )
                }

                is ReaderViewModel.Dialog.OrientationModeSelect -> {
                    OrientationSelectDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        onChange = { stringRes ->
                            showReaderNotice(stringRes)
                        },
                    )
                }

                is ReaderViewModel.Dialog.PageActions -> {
                    ReaderPageActionsDialog(
                        onDismissRequest = onDismissRequest,
                        onSetAsCover = viewModel::setAsCover,
                        onShare = viewModel::shareImage,
                        // SY -->
                        onSave = { extra ->
                            // KMK -->
                            if (externalStoragePermissionNotGranted) {
                                permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                // KMK <--
                                viewModel.saveImage(extra)
                            }
                        },
                        onShareCombined = viewModel::shareImages,
                        onSaveCombined = {
                            // KMK -->
                            if (externalStoragePermissionNotGranted) {
                                permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                // KMK <--
                                viewModel.saveImages()
                            }
                        },
                        hasExtraPage = (state.dialog as? ReaderViewModel.Dialog.PageActions)?.extraPage != null,
                    )
                }

                is ReaderViewModel.Dialog.ChapterList -> Unit

                ReaderViewModel.Dialog.AutoScrollHelp -> DetailDialog(
                    title = stringResource(SYMR.strings.eh_autoscroll_help),
                    onDismissRequest = onDismissRequest,
                    confirmLabel = stringResource(MR.strings.action_ok),
                    onConfirm = onDismissRequest,
                ) {
                    TsuzukiText(stringResource(SYMR.strings.eh_autoscroll_help_message), style = TsuzukiTheme.typography.callout, color = TsuzukiTheme.colors.secondary)
                }

                ReaderViewModel.Dialog.BoostPageHelp -> DetailDialog(
                    title = stringResource(SYMR.strings.eh_boost_page_help),
                    onDismissRequest = onDismissRequest,
                    confirmLabel = stringResource(MR.strings.action_ok),
                    onConfirm = onDismissRequest,
                ) {
                    TsuzukiText(stringResource(SYMR.strings.eh_boost_page_help_message), style = TsuzukiTheme.typography.callout, color = TsuzukiTheme.colors.secondary)
                }

                ReaderViewModel.Dialog.RetryAllHelp -> DetailDialog(
                    title = stringResource(SYMR.strings.eh_retry_all_help),
                    onDismissRequest = onDismissRequest,
                    confirmLabel = stringResource(MR.strings.action_ok),
                    onConfirm = onDismissRequest,
                ) {
                    TsuzukiText(stringResource(SYMR.strings.eh_retry_all_help_message), style = TsuzukiTheme.typography.callout, color = TsuzukiTheme.colors.secondary)
                }
                // SY <--
                null -> {}
            }
            }
        }
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        composeReaderHost?.destroy()
        composeReaderHost = null
        config = null
        readerNotices.value = null
    }

    override fun onPause() {
        lifecycleScope.launchNonCancellable {
            // KMK -->
            viewModel.flushReaderState()
            // KMK <--
        }


        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.restartReadTimer()


        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        val stage = readerStage
        if (stage?.isExiting == true) return
        if (stage != null && stage.canExit() && !isFinishing) {
            viewModel.onActivityFinish()
            stage.exit {
                readerStage = null
                super.finish()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
            return
        }
        viewModel.onActivityFinish()
        super.finish()
        val reduced = animationsReduced()
        val enter = if (reduced) 0 else R.anim.reader_close_enter
        val exit = if (reduced) 0 else R.anim.reader_close_exit
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enter, exit)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enter, exit)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = composeReaderHost?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = composeReaderHost?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    @Composable
    private fun ContentOverlay(state: ReaderViewModel.State) {
        val flashOnPageChange by readerPreferences.flashOnPageChange().collectAsState()

        val colorOverlayEnabled by readerPreferences.colorFilter().collectAsState()
        val colorOverlay by readerPreferences.colorFilterValue().collectAsState()
        val colorOverlayMode by readerPreferences.colorFilterMode().collectAsState()
        val colorOverlayBlendMode = remember(colorOverlayMode) {
            ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
        }

        ReaderContentOverlay(
            brightness = state.brightnessOverlayValue,
            color = colorOverlay.takeIf { colorOverlayEnabled },
            colorBlendMode = colorOverlayBlendMode,
        )

        if (flashOnPageChange) {
            DisplayRefreshHost(hostState = displayRefreshHost)
        }
    }

    @Composable
    fun AppBars(state: ReaderViewModel.State, glass: GlassContext, chromeReady: Boolean = true) {
        if (!ifSourcesLoaded()) {
            return
        }

        val isHttpSource = viewModel.getSource() is HttpSource

        val cropBorderPaged by readerPreferences.cropBorders().collectAsState()
        val cropBorderWebtoon by readerPreferences.cropBordersWebtoon().collectAsState()
        // SY -->
        val readingMode = viewModel.getMangaReadingMode()
        val isPagerType = ReadingMode.isPagerType(readingMode)
        val isWebtoon = ReadingMode.WEBTOON.flagValue == readingMode
        val cropBorderContinuousVertical by readerPreferences.cropBordersContinuousVertical().collectAsState()
        val cropEnabled = if (isPagerType) {
            cropBorderPaged
        } else if (isWebtoon) {
            cropBorderWebtoon
        } else {
            cropBorderContinuousVertical
        }
        val readerBottomButtons by readerPreferences.readerBottomButtons().changes().map { it.toImmutableSet() }
            .collectAsState(persistentSetOf())
        val dualPageSplitPaged by readerPreferences.dualPageSplitPaged().collectAsState()

        val betaReaderBarStyle by readerPreferences.betaReaderBarStyle().collectAsState()
        val chapterShortPattern = stringResource(MR.strings.tsuzuki_ch_short)
        val prevChapterShortName = remember(state.viewerChapters?.prevChapter, chapterShortPattern) {
            formatChapterShortName(state.viewerChapters?.prevChapter, chapterShortPattern)
        }
        val nextChapterShortName = remember(state.viewerChapters?.nextChapter, chapterShortPattern) {
            formatChapterShortName(state.viewerChapters?.nextChapter, chapterShortPattern)
        }
        val currentChapterShortName = remember(state.viewerChapters?.currChapter, chapterShortPattern) {
            formatChapterShortName(state.viewerChapters?.currChapter, chapterShortPattern)
        }
        val forceHorizontalSeekbar by readerPreferences.forceHorizontalSeekbar().collectAsState()
        val landscapeVerticalSeekbar by readerPreferences.landscapeVerticalSeekbar().collectAsState()
        val leftHandedVerticalSeekbar by readerPreferences.leftVerticalSeekbar().collectAsState()
        val configuration = LocalConfiguration.current
        val verticalSeekbarLandscape =
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && landscapeVerticalSeekbar
        val verticalSeekbarHorizontal = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val viewerIsVertical = composeReaderHost?.isWebtoon == true ||
            ReadingMode.fromPreference(readingMode) == ReadingMode.VERTICAL
        val showVerticalSeekbar =
            !forceHorizontalSeekbar && (verticalSeekbarLandscape || verticalSeekbarHorizontal) && viewerIsVertical
        val navBarType = when {
            !showVerticalSeekbar -> NavBarType.Bottom
            leftHandedVerticalSeekbar -> NavBarType.VerticalLeft
            else -> NavBarType.VerticalRight
        }
        // SY <--

        ReaderAppBars(
            visible = state.menuVisible && chromeReady,
            glass = glass,

            mangaTitle = state.manga?.title,
            chapterTitle = state.currentChapter?.chapter?.name,
            navigateUp = onBackPressedDispatcher::onBackPressed,
            onClickTopAppBar = ::openMangaScreen,
            bookmarked = state.bookmarked,
            onToggleBookmarked = viewModel::toggleChapterBookmark,
            onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
            onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
            onShare = ::shareChapter.takeIf { isHttpSource },

            onNextChapter = ::loadNextChapter,
            enabledNext = state.viewerChapters?.nextChapter != null,
            onPreviousChapter = ::loadPreviousChapter,
            enabledPrevious = state.viewerChapters?.prevChapter != null,
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onPageIndexChange = ::moveToPageIndex,

            readingMode = ReadingMode.fromPreference(
                viewModel.getMangaReadingMode(resolveDefault = false),
            ),
            onClickReadingMode = viewModel::openReadingModeSelectDialog,
            orientation = ReaderOrientation.fromPreference(
                viewModel.getMangaOrientation(resolveDefault = false),
            ),
            onClickOrientation = viewModel::openOrientationModeSelectDialog,
            cropEnabled = cropEnabled,
            onClickCropBorder = {
                val enabled = viewModel.toggleCropBorders()
                showReaderNotice(if (enabled) MR.strings.on else MR.strings.off)
            },
            onClickSettings = viewModel::openSettingsDialog,
            // SY -->
            isExhToolsVisible = state.ehUtilsVisible,
            onSetExhUtilsVisibility = viewModel::showEhUtils,
            isAutoScroll = state.autoScroll,
            isAutoScrollEnabled = state.isAutoScrollEnabled,
            onToggleAutoscroll = viewModel::toggleAutoScroll,
            autoScrollFrequency = state.ehAutoscrollFreq,
            onSetAutoScrollFrequency = viewModel::setAutoScrollFrequency,
            onClickAutoScrollHelp = viewModel::openAutoScrollHelpDialog,
            onClickRetryAll = ::exhRetryAll,
            onClickRetryAllHelp = viewModel::openRetryAllHelp,
            onClickBoostPage = ::exhBoostPage,
            onClickBoostPageHelp = viewModel::openBoostPageHelp,
            currentPageText = state.currentPageText,
            navBarType = navBarType,
            enabledButtons = readerBottomButtons,
            currentReadingMode = ReadingMode.fromPreference(
                viewModel.getMangaReadingMode(resolveDefault = true),
            ),
            dualPageSplitEnabled = dualPageSplitPaged,
            doublePages = state.doublePages,
            onClickChapterList = viewModel::openChapterListDialog,
            onClickPageLayout = {
                if (readerPreferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                    composeReaderHost?.toggleDoublePages()
                } else {
                    readerPreferences.pageLayout().set(1 - readerPreferences.pageLayout().get())
                }
            },
            onClickShiftPage = ::shiftDoublePages,
            barStyle = betaReaderBarStyle,
            prevChapterShortName = prevChapterShortName,
            nextChapterShortName = nextChapterShortName,
            currentChapterShortName = currentChapterShortName,
            // SY <--
            pageProvider = { index -> state.viewerChapters?.currChapter?.pages?.getOrNull(index) },
            isWebtoon = composeReaderHost?.isWebtoon == true,
            webtoonProgressFlow = composeReaderHost?.webtoonProgressFlow,
            onScrubWebtoonFraction = { fraction -> composeReaderHost?.moveToWebtoonFraction(fraction) },
            onSaveCapturedPng = { page, pngBytes ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                ) {
                    toast(KMR.strings.permission_writing_external_storage_succeed)
                } else {
                    viewModel.saveCapturedBitmap(page, pngBytes)
                }
            },
            onShareCapturedPng = viewModel::shareCapturedBitmap,
            onCaptureViewport = { onCaptured ->
                dev.errnolink.tsuzuki.ui.reader.captureReaderViewport(this, composeReaderHost?.view, onCaptured)
            },
        )
    }

    // KMK -->

    // EXH -->
    private fun enableExhAutoScroll() {
        readerPreferences.autoscrollInterval().changes()
            .combine(viewModel.state.map { it.autoScroll }.distinctUntilChanged()) { interval, enabled ->
                interval.toDouble() to enabled
            }.mapLatest { (intervalFloat, enabled) ->
                if (enabled) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val interval = intervalFloat.seconds
                        while (true) {
                            if (!viewModel.state.value.menuVisible) {
                                composeReaderHost?.autoscrollTick()
                                delay(interval)
                            } else {
                                delay(100)
                            }
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun exhRetryAll() {
        var retried = 0

        viewModel.state.value.viewerChapters
            ?.currChapter
            ?.pages
            ?.forEachIndexed { _, page ->
                var shouldQueuePage = false
                if (page.status is Page.State.Error) {
                    shouldQueuePage = true
                } /*else if (page.status == Page.State.LoadPage ||
                                    page.status == Page.State.DownloadImage) {
                                // Do nothing
                            }*/

                if (shouldQueuePage) {
                    page.status = Page.State.Queue
                } else {
                    return@forEachIndexed
                }

                val loader = page.chapter.pageLoader
                if (page.index == exhCurrentpage()?.index && loader is HttpPageLoader) {
                    loader.boostPage(page)
                } else {
                    loader?.retryPage(page)
                }

                retried++
            }

        toast(pluralStringResource(SYMR.plurals.eh_retry_toast, retried, retried))
    }

    private fun exhBoostPage() {
        if (composeReaderHost == null) return
        val curPage = exhCurrentpage() ?: run {
            toast(SYMR.strings.eh_boost_page_invalid)
            return
        }

        if (curPage.status is Page.State.Error) {
            toast(SYMR.strings.eh_boost_page_errored)
        } else if (curPage.status == Page.State.LoadPage || curPage.status == Page.State.DownloadImage) {
            toast(SYMR.strings.eh_boost_page_downloading)
        } else if (curPage.status == Page.State.Ready) {
            toast(SYMR.strings.eh_boost_page_downloaded)
        } else {
            val loader = (viewModel.state.value.viewerChapters?.currChapter?.pageLoader as? HttpPageLoader)
            if (loader != null) {
                loader.boostPage(curPage)
                toast(SYMR.strings.eh_boost_boosted)
            } else {
                toast(SYMR.strings.eh_boost_invalid_loader)
            }
        }
    }

    private fun exhCurrentpage(): ReaderPage? = composeReaderHost?.lastSelectedKomikkuPage

    fun reloadChapters(doublePages: Boolean, force: Boolean = false) {
        composeReaderHost?.setDoublePages(doublePages)
    }

    private fun shiftDoublePages() {
        composeReaderHost?.shiftDoublePages()
    }
    // EXH <--

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else if (readerPreferences.fullscreen().get()) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Called from the presenter when a manga is ready. Mounts the Compose reader as sole viewer.
     */
    private fun updateViewer() {
        val hadHost = composeReaderHost != null
        composeReaderHost?.destroy()
        binding.readerContainer.removeAllViews()
        val host = dev.errnolink.tsuzuki.ui.reader.ComposeReaderHost(activity = this)
        composeReaderHost = host
        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientation())
            }
        } else {
            setOrientation(viewModel.getMangaOrientation())
        }
        updateViewerInset(readerPreferences.fullscreen().get(), readerPreferences.drawUnderCutout().get())
        binding.readerContainer.addView(host.view)
        val manga = viewModel.state.value.manga
        val defaultReaderType = manga?.defaultReaderType(
            manga.mangaType(sourceName = sourceManager.get(manga.source)?.name),
        )
        if (
            readerPreferences.useAutoWebtoon().get() &&
            (manga?.readingMode?.toInt() ?: ReadingMode.DEFAULT.flagValue) == ReadingMode.DEFAULT.flagValue &&
            defaultReaderType != null &&
            defaultReaderType == ReadingMode.WEBTOON.flagValue
        ) {
            showReaderNotice(SYMR.strings.eh_auto_webtoon_snack)
        } else if (hadHost && readerPreferences.showReadingMode().get()) {
            showReadingModeToast(viewModel.getMangaReadingMode())
        }
        startPostponedEnterTransition()
    }

    private fun openMangaScreen() {
        viewModel.manga?.id?.let { id ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Constants.SHORTCUT_MANGA
                    putExtra(Constants.MANGA_EXTRA, id)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    private fun openChapterInBrowser() {
        assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    private fun shareChapter() {
        assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(intent)
        }
    }

    /** The in-app reader notice (reading mode, orientation, quick toggles): a pill under the top row (T5). */
    private fun showReaderNotice(stringRes: dev.icerock.moko.resources.StringResource) {
        readerNotices.value = dev.errnolink.tsuzuki.ui.reader.ReaderNotice(stringRes, ++noticeSeq)
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            showReaderNotice(ReadingMode.fromPreference(mode).stringRes)
        } catch (_: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    @SuppressLint("RestrictedApi")
    private fun setChapters(viewerChapters: ViewerChapters) {
        val state = viewModel.state.value
        if (state.indexChapterToShift != null && state.indexPageToShift != null) {
            viewModel.setIndexChapterToShift(null)
            viewModel.setIndexPageToShift(null)
        }
        composeReaderHost?.setChapters(viewerChapters)

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }

    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (show) {
            viewModel.showLoadingDialog()
        } else {
            viewModel.closeDialog()
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        composeReaderHost?.moveToPageIndex(index)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown. Also used by the beta Compose reader host.
     */
    fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown. Also used by the beta Compose reader host.
     */
    fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean = false) {
        // SY -->
        val currentPageText = if (hasExtraPage) {
            val invertDoublePage = readerPreferences.invertDoublePages().get()
            if ((resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) xor
                invertDoublePage
            ) {
                "${page.number}-${page.number + 1}"
            } else {
                "${page.number + 1}-${page.number}"
            }
        } else {
            "${page.number}"
        }
        // SY <--
        viewModel.onPageSelected(page, /* SY --> */ currentPageText, hasExtraPage /* SY <-- */)
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage, /* SY --> */ extraPage: ReaderPage? = null /* SY <-- */) {
        viewModel.openPageDialog(page, /* SY --> */ extraPage /* SY <-- */)
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchIO { viewModel.preload(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    fun onShareImageResult(uri: Uri, page: ReaderPage /* SY --> */, secondPage: ReaderPage? = null /* SY <-- */) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = // SY -->
            if (secondPage != null) {
                stringResource(
                    SYMR.strings.share_pages_info,
                    manga.title,
                    chapter.name,
                    if (resources.configuration.layoutDirection ==
                        View.LAYOUT_DIRECTION_LTR
                    ) {
                        "${page.number}-${page.number + 1}"
                    } else {
                        "${page.number + 1}-${page.number}"
                    },
                )
            } else {
                // SY <--
                stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number)
            },
        )
        startActivity(intent)
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    private fun updateViewerInset(fullscreen: Boolean, drawUnderCutout: Boolean) {
        if (!::binding.isInitialized) return
        val view = binding.readerContainer

        view.applyInsetsPadding(ViewCompat.getRootWindowInsets(view), fullscreen, drawUnderCutout)
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, windowInsets ->
            view.applyInsetsPadding(windowInsets, fullscreen, drawUnderCutout)
            windowInsets
        }
    }

    private fun View.applyInsetsPadding(
        windowInsets: WindowInsetsCompat?,
        fullscreen: Boolean,
        drawUnderCutout: Boolean,
    ) {
        val insets = when {
            !fullscreen -> windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
            !drawUnderCutout -> windowInsets?.getInsets(WindowInsetsCompat.Type.displayCutout())
            else -> null
        }
            ?: Insets.NONE

        setPadding(insets.left, insets.top, insets.right, insets.bottom)
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        /*
         * Initializes the reader subscriptions.
         */
        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    binding.readerContainer.setBackgroundColor(readerCanvasArgb(theme, baseContext.isNightMode()))
                }
                .launchIn(lifecycleScope)

            preferences.displayProfile().changes()
                .onEach { setDisplayProfile(it) }
                .launchIn(lifecycleScope)

            readerPreferences.keepScreenOn().changes()
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach(::setCustomBrightness)
                .launchIn(lifecycleScope)

            combine(
                readerPreferences.grayscale().changes(),
                readerPreferences.invertedColors().changes(),
            ) { grayscale, invertedColors -> grayscale to invertedColors }
                .onEach { (grayscale, invertedColors) ->
                    setLayerPaint(grayscale, invertedColors)
                }
                .launchIn(lifecycleScope)

            combine(
                readerPreferences.fullscreen().changes(),
                readerPreferences.drawUnderCutout().changes(),
            ) { fullscreen, drawUnderCutout -> fullscreen to drawUnderCutout }
                .onEach { (fullscreen, drawUnderCutout) ->
                    updateViewerInset(fullscreen, drawUnderCutout)
                }
                .launchIn(lifecycleScope)

            // SY -->
            readerPreferences.pageLayout().changes()
                .drop(1)
                .onEach {
                    viewModel.setDoublePages(viewModel.state.value.doublePages)
                }
                .launchIn(lifecycleScope)

            readerPreferences.dualPageSplitPaged().changes()
                .drop(1)
                .onEach {
                    if (composeReaderHost == null) return@onEach
                    reloadChapters(
                        !it &&
                            when (readerPreferences.pageLayout().get()) {
                                PagerConfig.PageLayout.DOUBLE_PAGES -> true
                                PagerConfig.PageLayout.AUTOMATIC ->
                                    resources.configuration.orientation ==
                                        Configuration.ORIENTATION_LANDSCAPE
                                else -> false
                            },
                        true,
                    )
                }
                .launchIn(lifecycleScope)
            // SY <--
        }

        /**
         * Sets the display profile to [path].
         */
        private fun setDisplayProfile(path: String) {
            val file = UniFile.fromUri(baseContext, path.toUri())
            if (file != null && file.exists()) {
                val inputStream = file.openInputStream()
                val outputStream = ByteArrayOutputStream()
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val data = outputStream.toByteArray()
                TachiyomiImageDecoder.displayProfile = data
            }
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                readerPreferences.customBrightnessValue().changes()
                    .sample(100)
                    .onEach(::setCustomBrightnessValue)
                    .launchIn(lifecycleScope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            viewModel.setBrightnessOverlayValue(value)
        }
        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.readerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }
    }

}
