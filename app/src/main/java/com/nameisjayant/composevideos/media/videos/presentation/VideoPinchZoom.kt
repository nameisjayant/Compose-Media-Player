package com.nameisjayant.composevideos.media.videos.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.media.ui.MediaColors
import kotlinx.coroutines.delay

/** How far apart (or together) the fingers go, as a share of where they started, to switch. */
private const val PINCH_SWITCH_ZOOM = 0.08f

/** How long "Zoomed to fill" / "Original" stays up after a switch. */
private const val ZOOM_PILL_TIMEOUT_MS = 1_500L

/**
 * How much the fitted video has to grow to cover a [container] edge to edge, cropping the sides
 * (or top and bottom) that don't fit: 1 when the shapes already match.
 */
internal fun fillScale(container: IntSize, videoAspectRatio: Float): Float {
    if (container.width <= 0 || container.height <= 0 || videoAspectRatio <= 0f) return 1f
    val ratio = container.width.toFloat() / container.height / videoAspectRatio
    return maxOf(ratio, 1f / ratio)
}

/**
 * Two-finger pinches. [onZoom] gets how far the fingers have spread since they went down (above 1
 * apart, below 1 together) as they move, and [onEnd] gets the final amount once they lift.
 *
 * The pinch is taken in the initial pass and every touch in it is consumed, from the second finger
 * landing until the last one lifts, so the taps, swipes and swipe-down below never see it.
 */
internal fun Modifier.pinchToZoom(
    enabled: Boolean,
    onStart: () -> Unit,
    onZoom: (Float) -> Unit,
    onEnd: (Float) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!pinching && event.changes.count { it.pressed } >= 2) {
                pinching = true
                onStart()
            }
            if (pinching) {
                val step = event.calculateZoom()
                if (step != 1f) {
                    zoom *= step
                    onZoom(zoom)
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) onEnd(zoom)
    }
}

/** Whether a pinch that spread the fingers by [zoom] switches to fill, to fit, or (null) neither. */
internal fun pinchTarget(zoom: Float): Boolean? = when {
    zoom > 1f + PINCH_SWITCH_ZOOM -> true
    zoom < 1f - PINCH_SWITCH_ZOOM -> false
    else -> null
}

/**
 * "Zoomed to fill" or "Original" at the top of the video for a moment after a pinch switches it.
 * [switches] is bumped on every switch, restarting the timer.
 */
@Composable
internal fun BoxScope.ZoomModePill(zoomedToFill: Boolean, switches: Int, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(switches) {
        if (switches == 0) return@LaunchedEffect
        visible = true
        delay(ZOOM_PILL_TIMEOUT_MS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && enabled,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(300)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 20.dp),
    ) {
        Text(
            text = if (zoomedToFill) "Zoomed to fill" else "Original",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(CircleShape)
                .background(MediaColors.Glass)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
