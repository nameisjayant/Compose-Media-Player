package com.nameisjayant.composevideos.media.videos.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.nameisjayant.composevideos.media.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Small frames from a video, one every [intervalMs] from the start, for the seek bar to show while
 * scrubbing. Filled in as they decode, so any of [frames] may still be null.
 */
class SeekPreviews(val intervalMs: Long, val frames: List<Bitmap?>) {
    /** The decoded frame closest to [positionMs], or null while none has decoded yet. */
    fun frameAt(positionMs: Long): Bitmap? =
        nearestFrame(frames, (positionMs.toFloat() / intervalMs).roundToInt())
}

interface SeekPreviewSource {
    /** Emits [SeekPreviews] again after every frame decoded, and completes once they all have. */
    fun previews(video: Video): Flow<SeekPreviews>
}

/** Frames no further apart than this, so a short clip previews every second. */
private const val MIN_INTERVAL_MS = 1_000L

/** Caps the decoding work (and memory) for a long video; the interval widens to fit. */
private const val MAX_FRAMES = 60

/** Wide enough to stay sharp at the preview's size, small enough that a strip is a few MB. */
private const val FRAME_WIDTH = 256

/**
 * Decodes frames straight from the bundled file with [MediaMetadataRetriever]. It seeks to the exact
 * frame rather than the nearest keyframe: the clips have keyframes up to ten seconds apart, which
 * would show a different scene from the one the thumb is over.
 */
class SeekPreviewSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SeekPreviewSource {

    override fun previews(video: Video): Flow<SeekPreviews> = flow {
        val retriever = MediaMetadataRetriever()
        try {
            context.resources.openRawResourceFd(video.videoRes).use { fd ->
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs == null || durationMs <= 0) return@flow
            val intervalMs = maxOf(MIN_INTERVAL_MS, durationMs / (MAX_FRAMES - 1))
            val count = (durationMs / intervalMs).toInt() + 1
            val frames = arrayOfNulls<Bitmap>(count)
            for (index in previewOrder(count)) {
                currentCoroutineContext().ensureActive()
                frames[index] = retriever.frameAt(index * intervalMs) ?: continue
                emit(SeekPreviews(intervalMs, frames.toList()))
            }
        } finally {
            retriever.release()
        }
    }.flowOn(ioDispatcher)

    private fun MediaMetadataRetriever.frameAt(positionMs: Long): Bitmap? {
        val timeUs = positionMs * 1_000
        val option = MediaMetadataRetriever.OPTION_CLOSEST
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Bounds, not a size: the frame keeps its aspect ratio inside them.
            getScaledFrameAtTime(timeUs, option, FRAME_WIDTH, FRAME_WIDTH)
        } else {
            getFrameAtTime(timeUs, option)?.let { full ->
                val height = (FRAME_WIDTH * full.height.toFloat() / full.width).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(full, FRAME_WIDTH, height, true).also { if (it !== full) full.recycle() }
            }
        }
    }
}

/**
 * The order to decode [count] frames in: every eighth first, then the gaps between them, halving
 * each pass, so the whole bar has a rough preview in moments and fills in after.
 */
internal fun previewOrder(count: Int): List<Int> {
    val seen = BooleanArray(count)
    return buildList {
        for (step in intArrayOf(8, 4, 2, 1)) {
            for (index in 0 until count step step) {
                if (!seen[index]) {
                    seen[index] = true
                    add(index)
                }
            }
        }
    }
}

/** The non-null entry of [frames] closest to [index], the earlier one on a tie. */
internal fun <T : Any> nearestFrame(frames: List<T?>, index: Int): T? {
    if (frames.isEmpty()) return null
    val target = index.coerceIn(0, frames.lastIndex)
    for (distance in 0..frames.size) {
        frames.getOrNull(target - distance)?.let { return it }
        frames.getOrNull(target + distance)?.let { return it }
    }
    return null
}
