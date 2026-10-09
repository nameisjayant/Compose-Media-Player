package com.nameisjayant.composevideos.media.videos.presentation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.media.ui.MediaColors
import com.nameisjayant.composevideos.media.videos.data.findTimestamps

private val TrackHeight = 4.dp

/** The chapter under the finger swells to this while scrubbing, like YouTube's. */
private val DraggedTrackHeight = 8.dp

/** The break in the track where one chapter ends and the next starts. */
private val ChapterGap = 2.dp

private val ThumbSize = 14.dp
/** Also the width the slider gives the thumb, so the track runs from half of it in at each end. */
internal val DraggedThumbSize = 18.dp

/**
 * The seek bar's track, split into one segment per chapter with a small gap at each start in
 * [chapterStarts] (fractions of the video, the first at 0). Filled up to [fraction].
 * With no chapters it's one plain track.
 */
@Composable
internal fun ChapterTrack(
    fraction: Float,
    chapterStarts: List<Float>,
    isDragging: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val grow by animateFloatAsState(if (isDragging) 1f else 0f, label = "trackGrow")
    val inactive = Color.White.copy(alpha = if (enabled) 0.3f else 0.15f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(DraggedTrackHeight),
    ) {
        val bounds = (chapterStarts.ifEmpty { listOf(0f) } + 1f).zipWithNext()
        val gap = ChapterGap.toPx()
        val progressX = fraction.coerceIn(0f, 1f) * size.width
        val current = bounds.indexOfLast { (start, _) -> start <= fraction }
        bounds.forEachIndexed { index, (start, end) ->
            val left = start * size.width + if (index > 0) gap / 2 else 0f
            val right = end * size.width - if (index < bounds.lastIndex) gap / 2 else 0f
            if (right <= left) return@forEachIndexed
            val height = if (index == current) {
                TrackHeight.toPx() + (DraggedTrackHeight - TrackHeight).toPx() * grow
            } else {
                TrackHeight.toPx()
            }
            val top = (size.height - height) / 2
            val radius = CornerRadius(height / 2)
            drawRoundRect(inactive, Offset(left, top), Size(right - left, height), radius)
            val filled = progressX.coerceAtMost(right) - left
            if (filled > 0f) {
                drawRoundRect(MediaColors.Accent, Offset(left, top), Size(filled, height), radius)
            }
        }
    }
}

/** A round thumb that swells while it's being dragged. Takes the same room either way, so the track doesn't shift. */
@Composable
internal fun SeekThumb(isDragging: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val size by animateDpAsState(if (isDragging) DraggedThumbSize else ThumbSize, label = "thumbSize")
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(DraggedThumbSize)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (enabled) MediaColors.Accent else MediaColors.Accent.copy(alpha = 0.4f)),
        )
    }
}

/** The description, with every time in it (chapters included) a link that jumps the video there. */
@Composable
internal fun DescriptionText(
    description: String,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val text = remember(description) {
        buildAnnotatedString {
            append(description)
            findTimestamps(description).forEach { timestamp ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "seek:${timestamp.positionMs}",
                        styles = TextLinkStyles(SpanStyle(color = MediaColors.Accent, fontWeight = FontWeight.Medium)),
                    ) { currentOnSeekTo(timestamp.positionMs) },
                    start = timestamp.range.first,
                    end = timestamp.range.last + 1,
                )
            }
        }
    }
    Text(
        text = text,
        color = MediaColors.OnCanvas,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
