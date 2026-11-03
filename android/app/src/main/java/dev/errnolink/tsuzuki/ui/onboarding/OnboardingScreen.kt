package dev.errnolink.tsuzuki.ui.onboarding

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassButton
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val steps = remember {
        listOf(
            ThemeStep(),
            StorageStep(),
            PermissionStep(),
            GuidesStep(onRestoreBackup = onRestoreBackup),
        )
    }

    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val isLastStep = currentStep == steps.lastIndex
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val motion = TsuzukiTheme.motion
    val scope = rememberCoroutineScope()
    val seekable = remember { SeekableTransitionState(0) }
    val backCurve = remember { CubicBezierEasing(0.1f, 0.1f, 0f, 1f) }
    val slidePx = with(LocalDensity.current) { M3Motion.SharedAxisSlide.roundToPx() }
    val fadeOutMs = (M3Motion.Long1 * M3Motion.FadeThroughThreshold).roundToInt()
    fun goToStep(step: Int) {
        currentStep = step
        scope.launch {
            seekable.animateTo(
                step,
                if (motion.reduced) M3Motion.reducedFade() else M3Motion.emphasized(M3Motion.Long1),
            )
        }
    }

    PredictiveBackHandler(enabled = currentStep != 0) { progress ->
        if (motion.reduced) {
            progress.collect()
            goToStep(currentStep - 1)
        } else {
            val target = currentStep - 1
            try {
                progress.collect { event ->
                    seekable.seekTo(M3Motion.StandardDecelerate.transform(event.progress), target)
                }
                currentStep = target
                seekable.animateTo(target, M3Motion.emphasized(M3Motion.Long1))
            } catch (e: CancellationException) {
                seekable.animateTo(seekable.currentState, tween(M3Motion.Short4, easing = backCurve))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TsuzukiTheme.colors.background)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(TsuzukiTheme.spacing.section))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = TsuzukiTheme.spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tsuzuki),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(TsuzukiTheme.colors.text),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(TsuzukiTheme.spacing.row))
                TsuzukiText(
                    stringResource(MR.strings.onboarding_heading),
                    Modifier.fillMaxWidth(),
                    TsuzukiTheme.typography.title1,
                )
                Spacer(Modifier.height(TsuzukiTheme.spacing.small))
                TsuzukiText(
                    stringResource(MR.strings.onboarding_description),
                    Modifier.fillMaxWidth(),
                    TsuzukiTheme.typography.callout,
                    TsuzukiTheme.colors.secondary,
                )
            }
            Spacer(Modifier.height(TsuzukiTheme.spacing.section))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                rememberTransition(seekable, label = "onboarding-step").AnimatedContent(
                    transitionSpec = {
                        val forward = targetState > initialState
                        val dir = if (forward xor isRtl) 1 else -1
                        if (motion.reduced) {
                            (fadeIn(M3Motion.reducedFade()) togetherWith fadeOut(M3Motion.reducedFade()))
                        } else {
                            (
                                slideInHorizontally(M3Motion.emphasized(M3Motion.Long1)) { dir * slidePx } +
                                    fadeIn(
                                        tween(M3Motion.Long1 - fadeOutMs, fadeOutMs, easing = M3Motion.EmphasizedDecelerate),
                                    )
                                ) togetherWith (
                                slideOutHorizontally(M3Motion.emphasized(M3Motion.Long1)) { -dir * slidePx } +
                                    fadeOut(tween(fadeOutMs, easing = M3Motion.EmphasizedAccelerate))
                                )
                        }.using(SizeTransform(clip = false))
                    },
                ) { step ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                rememberSaveable(key = "onboarding-scroll-$step", saver = ScrollState.Saver) { ScrollState(0) },
                            )
                            .padding(vertical = TsuzukiTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
                    ) {
                        steps[step].Content()
                    }
                }
            }
            val stepDescription = stringResource(MR.strings.tsuzuki_step_n_of_m, currentStep + 1, steps.size)
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = stepDescription
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    }
                    .padding(bottom = TsuzukiTheme.spacing.small),
                horizontalArrangement = Arrangement.Center,
            ) {
                steps.indices.forEach { index ->
                    val selected = index == currentStep
                    val itemDescription = stringResource(MR.strings.tsuzuki_step_n_of_m, index + 1, steps.size)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(role = Role.RadioButton) { goToStep(index) }
                            .semantics {
                                stateDescription = itemDescription
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    if (selected) TsuzukiTheme.colors.accent else TsuzukiTheme.colors.separator,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(TsuzukiTheme.spacing.small))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TsuzukiTheme.spacing.gutter)
                    .padding(bottom = TsuzukiTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentStep > 0) {
                    GlassButton(rememberGlassContext(), onClick = { goToStep(currentStep - 1) }) {
                        TsuzukiText(stringResource(MR.strings.action_webview_back), color = TsuzukiTheme.colors.text)
                    }
                }
                PillButton(
                    label = stringResource(
                        if (isLastStep) MR.strings.onboarding_action_finish else MR.strings.onboarding_action_next,
                    ),
                    onClick = {
                        if (isLastStep) {
                            onComplete()
                        } else {
                            goToStep(currentStep + 1)
                        }
                    },
                    enabled = steps[currentStep].isComplete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
