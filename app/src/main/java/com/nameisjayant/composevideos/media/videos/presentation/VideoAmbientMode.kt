package com.nameisjayant.composevideos.media.videos.presentation

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.nameisjayant.composevideos.media.videos.data.SeekPreviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How often the glow catches up with the video; slow on purpose, so it drifts rather than flickers. */
private const val AMBIENT_SAMPLE_MS = 1_000L

/** How long one glow takes to melt into the next. */
private const val AMBIENT_FADE_MS = 1_200

/**
 * The frame is shrunk to about this wide before it's blown back up: a handful of colour patches,
 * which bilinear filtering smooths into a soft glow even where [blur] does nothing (below API 31).
 */
private const val AMBIENT_WIDTH = 24

/** How strongly the glow shows over the canvas; YouTube keeps it subtle so text stays readable. */
private const val AMBIENT_ALPHA = 0.55f

/** How far the glow reaches below the video, as a share of the video's height. */
internal const val AMBIENT_REACH = 0.9f

/**
 * The frame on screen right now, shrunk down to a few colour patches for [AmbientGlow], sampled
 * from the seek-bar previews (already decoded) rather than read back from the video surface.
 * Null until a frame near the playhead has decoded, and again when a new video starts, so the
 * last video's colours don't linger. Only samples while [enabled] and the app is visible.
 */
@Composable
internal fun rememberAmbientFrame(
    player: Player,
    seekPreviews: () -> SeekPreviews?,
    enabled: Boolean,
): State<ImageBitmap?> {
    val state = remember { mutableStateOf<ImageBitmap?>(null) }
    val currentSeekPreviews by rememberUpdatedState(seekPreviews)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player, enabled, lifecycle) {
        if (!enabled) {
            state.value = null
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var sampled: Bitmap? = null
            while (true) {
                val frame = currentSeekPreviews()?.frameAt(player.currentPosition)
                if (frame !== sampled) {
                    sampled = frame
                    state.value = frame?.let { withContext(Dispatchers.Default) { it.toAmbient() } }
                }
                delay(AMBIENT_SAMPLE_MS)
            }
        }
    }
    return state
}

/**
 * YouTube's ambient mode: the current [frame] stretched over this box, blurred and faded out
 * towards the bottom, so the video's colours spill softly onto the page beneath it. Lay it out
 * from the top of the video down past its bottom edge; the video itself covers the top part.
 */
@Composable
internal fun AmbientGlow(frame: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier
            // Its own layer, so the fade below cuts into the glow alone and not the page under it.
            .graphicsLayer {
                alpha = AMBIENT_ALPHA
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Full strength behind the video, thinning out to nothing at the bottom.
                val videoBottom = 1f / (1f + AMBIENT_REACH)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        videoBottom to Color.Black.copy(alpha = 0.8f),
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Crossfade(targetState = frame, animationSpec = tween(AMBIENT_FADE_MS), label = "ambientGlow") { shown ->
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp, BlurredEdgeTreatment.Unbounded),
                )
            }
        }
    }
}

/** Shrinks a preview frame to [AMBIENT_WIDTH] across, halving each step so every pixel counts. */
private fun Bitmap.toAmbient(): ImageBitmap {
    var current = this
    for (size in ambientScaleSteps(IntSize(width, height))) {
        val next = Bitmap.createScaledBitmap(current, size.width, size.height, true)
        if (current !== this && next !== current) current.recycle()
        current = next
    }
    // Never hand out the preview itself: the seek bar still needs it, unscaled and unrecycled.
    if (current === this) current = copy(config ?: Bitmap.Config.ARGB_8888, false)
    return current.asImageBitmap()
}

/**
 * The sizes to shrink [size] through to get it [AMBIENT_WIDTH] wide, keeping its shape. Halving
 * at most each step, because bilinear filtering only looks at the four nearest pixels and a single
 * big jump would sample a few stray ones instead of averaging the picture.
 */
internal fun ambientScaleSteps(size: IntSize): List<IntSize> {
    if (size.width <= AMBIENT_WIDTH || size.height <= 0) return emptyList()
    val aspect = size.height.toFloat() / size.width
    return buildList {
        var width = size.width
        while (width > AMBIENT_WIDTH) {
            width = maxOf(width / 2, AMBIENT_WIDTH)
            add(IntSize(width, maxOf((width * aspect).toInt(), 1)))
        }
    }
}
