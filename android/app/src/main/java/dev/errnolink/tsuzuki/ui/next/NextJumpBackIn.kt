package dev.errnolink.tsuzuki.ui.next

import dev.errnolink.tsuzuki.reader.readerOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import tachiyomi.domain.manga.model.MangaCover

@Composable
fun NextJumpBackIn(
    title: String,
    chapter: String,
    progressLabel: String?,
    progress: Float?,
    cover: MangaCover,
    onContinue: () -> Unit,
    onOpenTitle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.grouped)
            .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onOpenTitle),
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.6f },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.15f))),
            ),
        )
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellCover(cover, title, Modifier.width(128.dp).clip(RoundedCornerShape(12.dp)), fixedRatio = 2f / 3f)
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                TsuzukiText(
                    "JUMP BACK IN",
                    style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.8f),
                )
                TsuzukiText(
                    title,
                    Modifier.padding(top = 4.dp),
                    style = TsuzukiTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TsuzukiText(
                    listOfNotNull(chapter, progressLabel).joinToString(" · "),
                    Modifier.padding(top = 4.dp),
                    style = TsuzukiTheme.typography.footnote,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                progress?.let { fraction ->
                    Box(
                        Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Color.White))
                    }
                }
                Row(
                    Modifier
                        .padding(top = 14.dp)
                        .readerOrigin()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .clickable(role = Role.Button, onClickLabel = "Continue $title", onClick = onContinue)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TsuzukiIcons.Play, null, Modifier.size(16.dp), tint = Color.Black)
                    TsuzukiText(
                        "Continue",
                        style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Black,
                    )
                }
            }
        }
    }
}
