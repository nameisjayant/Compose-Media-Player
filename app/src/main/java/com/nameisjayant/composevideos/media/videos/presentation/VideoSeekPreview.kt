package com.nameisjayant.composevideos.media.videos.presentation

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.media.ui.MediaColors
import kotlin.math.roundToInt

private val PreviewShape = RoundedCornerShape(8.dp)

/** Between the bottom of the preview and the top of the slider it sits over. */
private val PreviewGap = 8.dp

/**
 * The frame at the scrubbed-to time with the time under it, like YouTube's, growing in from the
 * bar while [visible]. A dark 16:9 box stands in until a frame near there has decoded.
 */
@Composable
internal fun SeekPreview(visible: Boolean, frame: Bitmap?, time: String, width: Dp, modifier: Modifier = Modifier) {
    // Kept out of the seek bar's Row, whose scope would otherwise claim this AnimatedVisibility.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        SeekPreviewCard(frame = frame, time = time, width = width)
    }
}

@Composable
private fun SeekPreviewCard(frame: Bitmap?, time: String, width: Dp) {
    val image = remember(frame) { frame?.asImageBitmap() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .width(width)
                .aspectRatio(frame?.let { it.width.toFloat() / it.height } ?: (16f / 9f))
                .clip(PreviewShape)
                .background(MediaColors.Surface)
                .border(1.dp, Color.White.copy(alpha = 0.8f), PreviewShape),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    // Only a picture of where the thumb is; the time below is what gets read out.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Text(
            text = time,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MediaColors.Glass)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Floats this above the slider it's laid out over, centred on the thumb at [fraction] (read while
 * placing, so dragging only moves it) and kept within the slider's ends. Takes up no height itself.
 */
internal fun Modifier.overSliderThumb(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val width = constraints.maxWidth
    layout(width, 0) {
        // The slider keeps half a thumb clear at each end, so that's where the track starts and stops.
        val inset = DraggedThumbSize.toPx() / 2
        val thumbX = inset + fraction().coerceIn(0f, 1f) * (width - 2 * inset)
        val x = (thumbX - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
        placeable.placeRelative(x, -placeable.height - PreviewGap.roundToPx())
    }
}
