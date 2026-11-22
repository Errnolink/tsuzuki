// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/ReaderTransitionPage.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransitionUiModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Pure, stateless interstitial transition page between adjacent chapters. Consumes an immutable
 * [ChapterTransitionUiModel] prepared by the domain layer.
 */
@Composable
fun ReaderTransitionPage(
    uiModel: ChapterTransitionUiModel,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onTap: ((PointF) -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
) {
    val tapModifier =
        if (onTap != null) {
            Modifier.pointerInput(onTap) {
                detectTapGestures(
                    onTap = { offset ->
                        val screenWidth = size.width.toFloat()
                        val screenHeight = size.height.toFloat()
                        if (screenWidth > 0 && screenHeight > 0) {
                            val pos = PointF(offset.x / screenWidth, offset.y / screenHeight)
                            onTap(pos)
                        } else {
                            onTap(PointF(0.5f, 0.5f))
                        }
                    }
                )
            }
        } else {
            Modifier
        }

    val isPrevWithoutTo = uiModel is ChapterTransitionUiModel.Prev && uiModel.toChapter == null

    Box(
        modifier =
            modifier
                .then(tapModifier)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top =
                        if (isPrevWithoutTo) {
                            88.dp
                        } else {
                            8.dp
                        },
                    bottom = 24.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val toChapter =
            when (uiModel) {
                is ChapterTransitionUiModel.Prev -> uiModel.toChapter
                is ChapterTransitionUiModel.Next -> uiModel.toChapter
            }

        GlassSurface(
            context = null,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (onCardClick != null && toChapter != null) {
                                Modifier.clickable(onClick = onCardClick)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (uiModel) {
                    is ChapterTransitionUiModel.Prev -> PrevChapterTransitionContent(uiModel)
                    is ChapterTransitionUiModel.Next -> NextChapterTransitionContent(uiModel)
                }

                val missingCount =
                    when (uiModel) {
                        is ChapterTransitionUiModel.Prev -> uiModel.missingChaptersCount
                        is ChapterTransitionUiModel.Next -> uiModel.missingChaptersCount
                    }
                MissingChapterWarningSection(missingChaptersCount = missingCount)

                if (toChapter != null) {
                    ChapterPreloadStatusSection(
                        preloadState = toChapter.preloadState,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrevChapterTransitionContent(uiModel: ChapterTransitionUiModel.Prev) {
    val prevChapter = uiModel.toChapter
    if (prevChapter != null) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            TsuzukiText(
                text = stringResource(MR.strings.transition_previous),
                style = TsuzukiTheme.typography.title3,
                color = TsuzukiTheme.colors.accent,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                TsuzukiText(
                    text = prevChapter.name,
                    style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Medium),
                    color = TsuzukiTheme.colors.text,
                    maxLines = 2,
                )
                if (prevChapter.isDownloaded != uiModel.isFromDownloaded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DownloadStatusIcon(isDownloaded = prevChapter.isDownloaded)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TsuzukiText(
                text = stringResource(MR.strings.transition_current),
                style = TsuzukiTheme.typography.title3,
                color = TsuzukiTheme.colors.accent,
            )
            TsuzukiText(
                text = uiModel.fromChapterName,
                style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Medium),
                color = TsuzukiTheme.colors.text,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
            )
        }
    } else {
        TsuzukiText(
            text = stringResource(MR.strings.transition_no_previous),
            style = TsuzukiTheme.typography.title3.copy(textAlign = TextAlign.Center),
            color = TsuzukiTheme.colors.text,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun NextChapterTransitionContent(uiModel: ChapterTransitionUiModel.Next) {
    val nextChapter = uiModel.toChapter
    if (nextChapter != null) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            TsuzukiText(
                text = stringResource(MR.strings.transition_finished),
                style = TsuzukiTheme.typography.title3,
                color = TsuzukiTheme.colors.accent,
            )
            TsuzukiText(
                text = uiModel.fromChapterName,
                style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Medium),
                color = TsuzukiTheme.colors.text,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(20.dp))

            TsuzukiText(
                text = stringResource(MR.strings.transition_next),
                style = TsuzukiTheme.typography.title3,
                color = TsuzukiTheme.colors.accent,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                TsuzukiText(
                    text = nextChapter.name,
                    style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Medium),
                    color = TsuzukiTheme.colors.text,
                    maxLines = 2,
                )
                if (nextChapter.isDownloaded != uiModel.isFromDownloaded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DownloadStatusIcon(isDownloaded = nextChapter.isDownloaded)
                }
            }
        }
    } else {
        TsuzukiText(
            text = stringResource(MR.strings.transition_no_next),
            style = TsuzukiTheme.typography.title3.copy(textAlign = TextAlign.Center),
            color = TsuzukiTheme.colors.text,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun DownloadStatusIcon(isDownloaded: Boolean) {
    Icon(
        imageVector = if (isDownloaded) Icons.Filled.Download else Icons.Outlined.Cloud,
        contentDescription = null,
        tint = TsuzukiTheme.colors.accent,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun MissingChapterWarningSection(missingChaptersCount: Int) {
    if (missingChaptersCount <= 0) return

    Spacer(modifier = Modifier.height(16.dp))
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    TsuzukiTheme.colors.destructive.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = TsuzukiTheme.colors.destructive,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TsuzukiText(
                text =
                    pluralStringResource(
                        MR.plurals.missing_chapters_warning,
                        missingChaptersCount,
                        missingChaptersCount,
                    ),
                style = TsuzukiTheme.typography.footnote,
                color = TsuzukiTheme.colors.text,
            )
        }
    }
}

@Composable
private fun ChapterPreloadStatusSection(
    preloadState: ChapterTransitionUiModel.PreloadState,
    onRetry: () -> Unit,
) {
    if (preloadState is ChapterTransitionUiModel.PreloadState.Error) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            TsuzukiText(
                text =
                    stringResource(
                        MR.strings.transition_pages_error,
                        preloadState.message,
                    ),
                style = TsuzukiTheme.typography.footnote.copy(textAlign = TextAlign.Center),
                color = TsuzukiTheme.colors.destructive,

            )
            Spacer(modifier = Modifier.height(8.dp))
            TsuzukiButton(label = stringResource(MR.strings.action_retry), onClick = onRetry)
        }
    }
}
