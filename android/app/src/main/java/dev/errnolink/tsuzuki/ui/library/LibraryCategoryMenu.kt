package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.designsystem.M3Motion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.LocalInsideGlass
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort

@Composable
fun LibraryCategoryMenu(
    expanded: Boolean,
    categories: List<Category>,
    currentCategoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!expanded) return

    val motion = TsuzukiTheme.motion
    val position = remember { CategoryMenuPositionProvider() }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.92f,
        animationSpec = if (motion.reduced) snap() else motion.sheetSpring(),
        label = "Category menu reveal scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (motion.reduced) snap() else tween(160, easing = M3Motion.Standard),
        label = "Category menu reveal alpha",
    )

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = (screenHeight * 0.60f).coerceAtLeast(240.dp)

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalInsideGlass provides false) {
            GlassSurface(
                context = null,
                shape = RoundedCornerShape(TsuzukiCorners.group),
                material = GlassMaterial.Thick,
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 180.dp, max = 280.dp)
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = position.origin
                    }
                    .semantics { paneTitle = "Categories" },
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    categories.forEachIndexed { index, category ->
                        val isCurrent = index == currentCategoryIndex
                        val name = category.name.ifBlank { "Default" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(role = Role.Button) {
                                    onSelectCategory(index)
                                    onDismissRequest()
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TsuzukiText(
                                text = name,
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                                color = if (isCurrent) TsuzukiTheme.colors.accent else TsuzukiTheme.colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = TsuzukiIcons.Check,
                                    contentDescription = "Selected",
                                    tint = TsuzukiTheme.colors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(TsuzukiSpacing.hairline)
                            .background(TsuzukiTheme.colors.separator),
                    )
                }
            }
        }
    }
}

private class CategoryMenuPositionProvider : PopupPositionProvider {
    var origin by mutableStateOf(TransformOrigin.Center)
        private set

    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val marginPx = 16
        val gapPx = 6
        val x = (anchorBounds.left).coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))
        val y = (anchorBounds.bottom + gapPx).coerceIn(marginPx, (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx))
        origin = TransformOrigin(
            pivotFractionX = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f),
            pivotFractionY = 0f,
        )
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}
