package exh.ui.intercept

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.presentation.components.AppBar
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import exh.source.getMainSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class InterceptActivity : BaseActivity() {
    private var statusJob: Job? = null

    private val status: MutableStateFlow<InterceptResult> = MutableStateFlow(InterceptResult.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        setComposeContent {
            InterceptActivityContent(status.collectAsState().value)
        }

        processLink()
    }

    @Composable
    private fun InterceptActivityContent(status: InterceptResult) {
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.app_name),
                    navigateUp = ::finish,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                when (status) {
                    InterceptResult.Idle, InterceptResult.Loading -> {
                        Text(
                            text = stringResource(SYMR.strings.loading_entry),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                    }
                    is InterceptResult.Success -> Text(
                        text = stringResource(SYMR.strings.launching_app),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    is InterceptResult.Failure -> Text(
                        text = stringResource(SYMR.strings.error_with_reason, status.reason),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }

    private fun processLink() {
        if (Intent.ACTION_VIEW == intent.action) {
            lifecycleScope.launchIO {
                // wait for sources to load
                Injekt.get<SourceManager>().isInitialized.first { it }
                loadGallery(intent.dataString!!)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        statusJob?.cancel()
        statusJob = status
            .onEach {
                when (it) {
                    is InterceptResult.Success -> {
                        finish()
                        startActivity(
                            if (it.chapter != null) {
                                ReaderActivity.newIntent(this, it.manga.id, it.chapter.id)
                            } else {
                                Intent(this, MainActivity::class.java)
                                    .setAction(Constants.SHORTCUT_MANGA)
                                    // KMK -->
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    // KMK <--
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(Constants.MANGA_EXTRA, it.mangaId)
                            },
                        )
                    }
                    is InterceptResult.Failure -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(MR.strings.chapter_error.getString(this))
                            .setMessage(stringResource(SYMR.strings.could_not_open_entry, it.reason))
                            .setPositiveButton(MR.strings.action_ok.getString(this), null)
                            .setOnCancelListener { finish() }
                            .setOnDismissListener { finish() }
                            .show()
                    }
                    else -> Unit
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onStop() {
        super.onStop()
        statusJob?.cancel()
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    private val sourceManager: SourceManager = Injekt.get()
    private val sourcePreferences: SourcePreferences = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get()
    private val getChapter: GetChapter = Injekt.get()

    private fun pickSource(url: String): List<UrlImportableSource> {
        val uri = url.toUri()
        val languages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledSources().get()
        return sourceManager.getVisibleSources()
            .mapNotNull { it.getMainSource<MangaDex>() }
            .filter { it.lang in languages && it.id.toString() !in disabledSources && it.matchesUri(uri) }
    }

    private suspend fun loadGallery(gallery: String) {
        // Do not load gallery if already loading
        if (status.value is InterceptResult.Idle) {
            status.value = InterceptResult.Loading
            val sources = pickSource(gallery)
            if (sources.size > 1) {
                withUIContext {
                    MaterialAlertDialogBuilder(this@InterceptActivity)
                        .setTitle(MR.strings.label_sources.getString(this@InterceptActivity))
                        .setSingleChoiceItems(sources.map { it.toString() }.toTypedArray(), 0) { dialog, index ->
                            dialog.dismiss()
                            lifecycleScope.launchIO {
                                loadGalleryEnd(gallery, sources[index])
                            }
                        }
                        .show()
                }
            } else {
                loadGalleryEnd(gallery, sources.firstOrNull())
            }
        }
    }

    private suspend fun loadGalleryEnd(gallery: String, source: UrlImportableSource?) {
        if (source == null) {
            status.value = InterceptResult.Failure(stringResource(SYMR.strings.batch_add_unknown_source_log_message, gallery))
            return
        }
        try {
            val uri = gallery.toUri()
            val chapterUrl = source.mapUrlToChapterUrl(uri)
            val cleanedChapterUrl = chapterUrl?.let(source::cleanChapterUrl)
            val mangaUrl = chapterUrl?.let { source.mapChapterUrlToMangaUrl(it.toUri()) }
                ?: source.mapUrlToMangaUrl(uri)
                ?: error(stringResource(SYMR.strings.batch_add_unknown_type_log_message, gallery))
            var manga = networkToLocalManga(
                Manga.create().copy(source = source.id, url = source.cleanMangaUrl(mangaUrl)),
            )
            manga = updateMangaFromRemote(
                manga = manga,
                fetchDetails = true,
                fetchChapters = true,
            ).getOrThrow().manga
            updateManga.awaitUpdateFavorite(manga.id, true)
            manga = manga.copy(favorite = true)
            val chapter = cleanedChapterUrl?.let {
                getChapter.await(it, manga.id)
                    ?: error(stringResource(SYMR.strings.gallery_adder_could_not_identify_chapter, gallery))
            }
            status.value = InterceptResult.Success(manga.id, manga, chapter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status.value = InterceptResult.Failure(e.message ?: e.toString())
        }
    }

    init {
        registerSecureActivity(this)
    }
}

sealed class InterceptResult {
    data object Idle : InterceptResult()
    data object Loading : InterceptResult()
    data class Success(val mangaId: Long, val manga: Manga, val chapter: Chapter? = null) : InterceptResult()
    data class Failure(val reason: String) : InterceptResult()
}
