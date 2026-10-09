package com.nameisjayant.composevideos.media.videos.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.R
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far one double tap (or each extra tap after it) skips. */
internal const val SEEK_STEP_MS = 10_000L

/** After a skip, further single taps on the same side keep skipping for this long. */
private const val SEEK_SESSION_MS = 700L

private const val RIPPLE_MS = 650

internal enum class SeekSide(val direction: Int) { Back(-1), Forward(1) }

/**
 * Which side is being skipped on right now, how far in total, and where the last tap landed, so
 * the ripple can grow out of the finger. The tap is measured from the edge on its side: from the
 * left for [SeekSide.Back], and from the right (so x is negative) for [SeekSide.Forward].
 */
@Stable
internal class SeekFeedbackState {
    var side by mutableStateOf<SeekSide?>(null)
        private set
    var totalSeconds by mutableIntStateOf(0)
        private set
    var tapPosition by mutableStateOf(Offset.Zero)
        private set

    /** Bumped on every skip, to restart the ripple and the session countdown. */
    var taps by mutableIntStateOf(0)
        private set

    fun isSeeking(side: SeekSide) = this.side == side

    fun onSeek(side: SeekSide, position: Offset) {
        if (this.side != side) totalSeconds = 0
        this.side = side
        totalSeconds += (SEEK_STEP_MS / 1000).toInt()
        tapPosition = position
        taps++
    }

    fun end() {
        side = null
        totalSeconds = 0
    }
}

/**
 * A single tap calls [onTap] (once it's clear no second tap is coming); a double tap on the left
 * or right half calls [onSeek] for that side, with the tap measured as [SeekFeedbackState] keeps
 * it. While a skip on one side is still showing, each further single tap there skips again straight
 * away, so tapping fast keeps adding 10 seconds.
 *
 * Drags are left alone, so the player can still be swiped down.
 */
internal fun Modifier.doubleTapToSeek(
    enabled: Boolean,
    seekEnabled: () -> Boolean,
    isSeeking: (SeekSide) -> Boolean,
    onTap: () -> Unit,
    onSeek: (SeekSide, Offset) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    fun sideOf(position: Offset) = if (position.x < size.width / 2f) SeekSide.Back else SeekSide.Forward
    fun fromEdge(side: SeekSide, position: Offset) =
        if (side == SeekSide.Forward) position.copy(x = position.x - size.width) else position

    awaitEachGesture {
        awaitFirstDown()
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        val side = sideOf(up.position)

        if (!seekEnabled()) {
            onTap()
            return@awaitEachGesture
        }
        if (isSeeking(side)) {
            up.consume()
            onSeek(side, fromEdge(side, up.position))
            return@awaitEachGesture
        }

        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
        if (secondDown == null) {
            onTap()
            return@awaitEachGesture
        }
        val secondUp = waitForUpOrCancellation() ?: return@awaitEachGesture
        secondUp.consume()
        val secondSide = sideOf(secondUp.position)
        onSeek(secondSide, fromEdge(secondSide, secondUp.position))
    }
}

/** One expanding circle from a tap, measured as [SeekFeedbackState.tapPosition] is. */
private class SeekRipple(val side: SeekSide, val center: Offset) {
    val progress = Animatable(0f)
}

/**
 * The curved, lightly shaded half of the video on the side being skipped, with a ripple from the
 * tap, chevrons and the running total. Ends the skip session once the taps stop.
 *
 * Every tap gets its own ripple, so fast taps overlap smoothly instead of restarting one.
 */
@Composable
internal fun BoxScope.SeekFeedback(state: SeekFeedbackState) {
    val scope = rememberCoroutineScope()
    val ripples = remember { mutableStateListOf<SeekRipple>() }
    val currentState by rememberUpdatedState(state)
    // A quick swell on each tap, easing back down.
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(state.taps) {
        if (state.taps == 0) return@LaunchedEffect
        val side = state.side ?: return@LaunchedEffect
        val ripple = SeekRipple(side, state.tapPosition)
        ripples.removeAll { it.side != side }
        ripples += ripple
        scope.launch {
            ripple.progress.animateTo(1f, tween(RIPPLE_MS, easing = LinearOutSlowInEasing))
            ripples -= ripple
        }
        scope.launch {
            pulse.snapTo(1f)
            pulse.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        delay(SEEK_SESSION_MS)
        currentState.end()
    }

    // Remembered so the side doesn't flip mid fade-out after the session ends.
    var shownSide by remember { mutableStateOf(SeekSide.Forward) }
    state.side?.let { shownSide = it }
    val isForward = shownSide == SeekSide.Forward
    val edge = if (isForward) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)

    AnimatedVisibility(
        visible = state.side != null,
        enter = fadeIn(tween(150)) + scaleIn(tween(250, easing = FastOutSlowInEasing), initialScale = 0.85f, transformOrigin = edge),
        exit = fadeOut(tween(300, easing = LinearOutSlowInEasing)),
        modifier = Modifier
            .align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight()
            .fillMaxWidth(0.45f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(if (isForward) ForwardArc else BackArc)
                .drawBehind {
                    drawRect(Color.White.copy(alpha = 0.12f + 0.06f * pulse.value))
                    val maxRadius = hypot(size.width, size.height) * 0.9f
                    ripples.forEach { ripple ->
                        if (ripple.side != shownSide) return@forEach
                        val progress = ripple.progress.value
                        val tap = ripple.center
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f * (1f - progress)),
                            radius = maxRadius * progress,
                            center = if (isForward) tap.copy(x = size.width + tap.x) else tap,
                        )
                    }
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + 0.12f * pulse.value
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                SeekChevrons(forward = isForward)
                Spacer(Modifier.height(6.dp))
                // The total rolls up (or down) to its new value rather than jumping.
                AnimatedContent(
                    targetState = state.totalSeconds.coerceAtLeast(SEEK_STEP_MS.toInt() / 1000),
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(tween(200)) { if (up) it else -it } + fadeIn(tween(200)))
                            .togetherWith(slideOutVertically(tween(200)) { if (up) -it else it } + fadeOut(tween(150)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "seekTotal",
                ) { seconds ->
                    Text(
                        text = "$seconds seconds",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** Three small arrows lighting up one after another, pointing the way of the skip. */
@Composable
private fun SeekChevrons(forward: Boolean) {
    val transition = rememberInfiniteTransition(label = "chevrons")
    Row(
        horizontalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = Modifier.graphicsLayer { scaleX = if (forward) 1f else -1f },
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 750
                        0.3f at 0
                        1f at 150 + index * 150
                        0.3f at 450 + index * 150
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "chevron$index",
            )
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

/** A half with a bowed inner edge, like the inside of a big circle centred off screen. */
private val ForwardArc = GenericShape { size, _ ->
    addOval(Rect(left = size.width * 0.15f, top = -size.height * 0.4f, right = size.width * 2.2f, bottom = size.height * 1.4f))
}

private val BackArc = GenericShape { size, _ ->
    addOval(Rect(left = -size.width * 1.2f, top = -size.height * 0.4f, right = size.width * 0.85f, bottom = size.height * 1.4f))
}
