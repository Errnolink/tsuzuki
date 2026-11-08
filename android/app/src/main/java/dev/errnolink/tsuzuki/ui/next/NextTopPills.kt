package dev.errnolink.tsuzuki.ui.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.pressScale

private val PillHeight = 48.dp

@Composable
fun NextTitlePill(
    glass: GlassContext?,
    title: String,
    caption: String?,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    menu: @Composable () -> Unit = {},
) {
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val interaction = remember { MutableInteractionSource() }
    Box(modifier) {
        GlassSurface(
            context = glass,
            shape = CircleShape,
            material = GlassMaterial.Thick,
            modifier = Modifier.height(PillHeight).widthIn(max = maxWidth).pressScale(interaction),
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
                    .semantics { contentDescription = listOfNotNull(title, caption).joinToString(", ") }
                    .padding(start = 13.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = colors.text)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.dp).height(26.dp).background(colors.separator))
                Spacer(Modifier.width(13.dp))
                AnimatedContent(
                    targetState = title to caption,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            fadeIn(tween(M3Motion.Short4, delayMillis = M3Motion.Short2)) togetherWith fadeOut(tween(M3Motion.Short2))
                        }
                    },
                    label = "Next title pill",
                ) { (shownTitle, shownCaption) ->
                    Column(verticalArrangement = Arrangement.Center) {
                        TsuzukiText(
                            shownTitle,
                            style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (shownCaption != null) {
                            TsuzukiText(
                                shownCaption,
                                style = TsuzukiTheme.typography.caption1,
                                color = colors.secondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        menu()
    }
}
