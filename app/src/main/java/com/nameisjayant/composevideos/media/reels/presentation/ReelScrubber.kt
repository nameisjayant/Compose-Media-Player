package com.nameisjayant.composevideos.media.reels.presentation

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.media.ui.MediaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private val ProgressBrush = Brush.horizontalGradient(listOf(MediaColors.AccentDeep, MediaColors.Accent))

/** Height of the touch strip around the hairline track, so it is easy to grab. */
private val ScrubTouchHeight = 26.dp
private val PreviewWidth = 96.dp
private val PreviewHeight = 170.dp
private val PreviewGap = 14.dp

/** Preview frames are grabbed per bucket of this many ms, so a slow drag doesn't decode every pixel's worth. */
private const val FRAME_BUCKET_MS = 250L

/**
 * Hairline progress track that can be dragged to seek. The player reports every frame, so
 * [progress] is read only while drawing. A horizontal drag pauses via [onScrubStart], shows a
 * frame preview above the finger, and hands the release point to [onScrubEnd] (null if the
 * gesture was taken over, e.g. by the pager).
 */
@Composable
internal fun ReelProgress(
    @RawRes videoRes: Int,
    progress: () -> Float,
    onScrubStart: () -> Unit,
    onScrubEnd: (fraction: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val trackHeight = animateDpAsState(if (scrubbing) 4.dp else 2.dp, label = "trackHeight")
    // Kept across drags of the same reel, so scrubbing back over a spot is instant.
    val frames = remember(videoRes) { HashMap<Long, ImageBitmap>() }
    val haptics = LocalHapticFeedback.current
    val currentOnStart by rememberUpdatedState(onScrubStart)
    val currentOnEnd by rememberUpdatedState(onScrubEnd)

    Box(
        modifier
            .fillMaxWidth()
            .height(ScrubTouchHeight)
            .pointerInput(Unit) {
                fun fractionAt(x: Float) = (x / size.width).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = {
                        scrubFraction = fractionAt(it.x)
                        scrubbing = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnStart()
                    },
                    onDragEnd = {
                        scrubbing = false
                        currentOnEnd(scrubFraction)
                    },
                    onDragCancel = {
                        scrubbing = false
                        currentOnEnd(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrubFraction = fractionAt(change.position.x)
                    },
                )
            }
            .drawBehind {
                val h = trackHeight.value.toPx()
                val top = (size.height - h) / 2
                val radius = CornerRadius(h / 2)
                val fraction = if (scrubbing) scrubFraction else progress()
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, top), Size(size.width, h), radius)
                drawRoundRect(ProgressBrush, Offset(0f, top), Size(size.width * fraction, h), radius)
                if (scrubbing) {
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(size.width * fraction, size.height / 2))
                }
            },
    ) {
        if (scrubbing) {
            ScrubPreview(
                videoRes = videoRes,
                fraction = { scrubFraction },
                frames = frames,
                // Takes no space in the row; floats above the track, centred on the finger but kept on screen.
                modifier = Modifier.layout { measurable, constraints ->
                    val preview = measurable.measure(Constraints())
                    val maxX = (constraints.maxWidth - preview.width).coerceAtLeast(0)
                    layout(0, 0) {
                        val x = (constraints.maxWidth * scrubFraction - preview.width / 2).toInt().coerceIn(0, maxX)
                        preview.place(x, -preview.height - PreviewGap.roundToPx())
                    }
                },
            )
        }
    }
}

/** Frame at the scrub point plus its timestamp, decoded off the main thread. */
@Composable
private fun ScrubPreview(
    @RawRes videoRes: Int,
    fraction: () -> Float,
    frames: MutableMap<Long, ImageBitmap>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(videoRes) {
        val (widthPx, heightPx) = with(density) { PreviewWidth.roundToPx() to PreviewHeight.roundToPx() }
        val retriever = withContext(Dispatchers.IO) {
            MediaMetadataRetriever().apply {
                context.resources.openRawResourceFd(videoRes).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
        }
        try {
            durationMs = withContext(Dispatchers.IO) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
            fun bucketAt(f: Float) = (durationMs * f).toLong() / FRAME_BUCKET_MS * FRAME_BUCKET_MS
            // Exact frames decode forward from the previous keyframe, which takes far longer than a
            // drag stays in one bucket. So never cancel a decode (its result would be thrown away and
            // nothing would ever show): show the cheap nearest keyframe first, and only refine to the
            // exact frame once the finger has settled. Conflation skips positions passed meanwhile.
            snapshotFlow { bucketAt(fraction()) }
                .distinctUntilChanged()
                .conflate()
                .collect { ms ->
                    frames[ms]?.let { frame = it; return@collect }
                    withContext(Dispatchers.IO) {
                        retriever.frameAt(ms, widthPx, heightPx, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }?.let { frame = it }
                    if (bucketAt(fraction()) != ms) return@collect
                    val exact = withContext(Dispatchers.IO) {
                        retriever.frameAt(ms, widthPx, heightPx, MediaMetadataRetriever.OPTION_CLOSEST)
                    } ?: return@collect
                    frames[ms] = exact
                    if (bucketAt(fraction()) == ms) frame = exact
                }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { retriever.release() }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier
                .size(PreviewWidth, PreviewHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MediaColors.SurfaceRaised)
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        ) {
            frame?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = "${formatTime((durationMs * fraction()).toLong())} / ${formatTime(durationMs)}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Frame near [ms]. [option] trades accuracy for speed: OPTION_CLOSEST_SYNC is one keyframe decode,
 * OPTION_CLOSEST decodes on from it, which is slow since the bundled clips have one every few seconds.
 */
private fun MediaMetadataRetriever.frameAt(ms: Long, width: Int, height: Int, option: Int): ImageBitmap? {
    val us = ms * 1_000
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(us, option, width, height)
    } else {
        getFrameAtTime(us, option)?.let { full ->
            Bitmap.createScaledBitmap(full, width, height, true).also { if (it !== full) full.recycle() }
        }
    }
    return bitmap?.asImageBitmap()
}

/** 4_300 → "0:04". */
private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1_000 % 60)
