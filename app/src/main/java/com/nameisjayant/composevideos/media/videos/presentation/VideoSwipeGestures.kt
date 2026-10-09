package com.nameisjayant.composevideos.media.videos.presentation

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.ui.MediaColors
import com.nameisjayant.composevideos.media.videos.data.Chapter
import com.nameisjayant.composevideos.media.videos.data.chapterAt
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a swipe across the whole width of the video seeks. */
private const val SWIPE_SEEK_RANGE_MS = 90_000L

/** The dimmest the window goes; 0 can leave some screens fully dark. */
private const val MIN_WINDOW_BRIGHTNESS = 0.01f

/** What a full-screen swipe is changing. */
internal enum class SwipeKind { Brightness, Volume, Seek }

/**
 * The full-screen swipe in progress: brightness or volume as a 0–1 level, or where a seek will
 * land. Reads its starting point when the swipe begins, so outside changes (volume keys) are
 * picked up next time.
 *
 * [player], [canSeek], [brightness] and the callbacks are refreshed on every composition.
 */
@Stable
internal class SwipeAdjustState(context: Context) {
    private val contentResolver = context.contentResolver
    private val audio = ContextCompat.getSystemService(context, AudioManager::class.java)

    var player: Player? = null
    var canSeek = false
    var brightness: Float? = null
    var onBrightnessChange: (Float) -> Unit = {}
    var onStart: () -> Unit = {}
    var onLimit: () -> Unit = {}

    var kind by mutableStateOf<SwipeKind?>(null)
        private set

    /** The brightness, or the volume rounded to the step it's actually at. */
    var level by mutableFloatStateOf(0f)
        private set
    var seekStart by mutableLongStateOf(0L)
        private set
    var seekTarget by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set

    // Where the finger has taken things, before rounding to volume steps or clamping a seek.
    private var rawLevel = 0f
    private var rawSeekOffset = 0f

    /** Starts a swipe of [kind], or returns false when there's nothing to change (fixed volume, unseekable video). */
    fun start(kind: SwipeKind): Boolean {
        when (kind) {
            SwipeKind.Brightness -> {
                rawLevel = brightness ?: systemBrightness()
                level = rawLevel
            }

            SwipeKind.Volume -> {
                val audio = audio?.takeUnless { it.isVolumeFixed } ?: return false
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max <= 0) return false
                rawLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                level = rawLevel
            }

            SwipeKind.Seek -> {
                val player = player ?: return false
                if (!canSeek || player.duration == C.TIME_UNSET || player.duration <= 0) return false
                duration = player.duration
                seekStart = player.currentPosition.coerceIn(0, duration)
                seekTarget = seekStart
                rawSeekOffset = 0f
            }
        }
        this.kind = kind
        onStart()
        return true
    }

    /** Moves by [fraction] of the video's height (brightness, volume; up is more) or width (seek). */
    fun drag(fraction: Float) {
        when (kind) {
            SwipeKind.Brightness -> {
                if (!moveLevel(fraction)) return
                level = rawLevel
                onBrightnessChange(rawLevel)
            }

            SwipeKind.Volume -> {
                if (!moveLevel(fraction)) return
                val audio = audio ?: return
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val index = (rawLevel * max).roundToInt()
                if (index != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
                }
                level = index.toFloat() / max
            }

            SwipeKind.Seek -> {
                rawSeekOffset += fraction * SWIPE_SEEK_RANGE_MS
                seekTarget = (seekStart + rawSeekOffset.toLong()).coerceIn(0, duration)
            }

            null -> Unit
        }
    }

    /** Lands the seek if the finger was lifted; a cancelled swipe leaves the video where it was. */
    fun end(completed: Boolean) {
        if (kind == SwipeKind.Seek && completed && seekTarget != seekStart) player?.seekTo(seekTarget)
        kind = null
    }

    /** Returns whether the level moved, ticking [onLimit] when it reaches either end. */
    private fun moveLevel(fraction: Float): Boolean {
        val next = (rawLevel + fraction).coerceIn(0f, 1f)
        if (next == rawLevel) return false
        if (next == 0f || next == 1f) onLimit()
        rawLevel = next
        return true
    }

    /** The system brightness as a 0–1 level, used until the user picks one for the player. */
    private fun systemBrightness(): Float {
        val value = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        return if (value < 0) 0.5f else (value / 255f).coerceIn(0f, 1f)
    }
}

@Composable
internal fun rememberSwipeAdjustState(): SwipeAdjustState {
    val context = LocalContext.current
    return remember(context) { SwipeAdjustState(context.applicationContext) }
}

/**
 * Full-screen swipes: up and down on the left third changes the brightness, on the right third the
 * volume, and a sideways drag anywhere seeks (landing when the finger lifts).
 *
 * Vertical drags in the middle third are left alone, so the player can still be swiped down into
 * the floating window. So are taps, which [doubleTapToSeek] handles.
 */
internal fun Modifier.swipeToAdjust(enabled: Boolean, state: SwipeAdjustState): Modifier =
    if (!enabled) this else pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val edgeKind = when {
                down.position.x < size.width / 3f -> SwipeKind.Brightness
                down.position.x > size.width * 2f / 3f -> SwipeKind.Volume
                else -> null
            }

            // Wait until the finger has clearly moved, then go by which way it went.
            var travel = Offset.Zero
            var kind: SwipeKind? = null
            while (kind == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                travel += change.positionChange()
                if (travel.getDistance() < viewConfiguration.touchSlop) continue
                val candidate = if (abs(travel.x) > abs(travel.y)) SwipeKind.Seek else edgeKind
                if (candidate == null || !state.start(candidate)) return@awaitEachGesture
                change.consume()
                kind = candidate
            }

            val completed = drag(down.id) { change ->
                val delta = change.positionChange()
                change.consume()
                state.drag(if (kind == SwipeKind.Seek) delta.x / size.width else -delta.y / size.height)
            }
            state.end(completed)
        }
    }

/** The brightness or volume level, or the seek target, in the middle of the video while swiping. */
@Composable
internal fun BoxScope.SwipeAdjustFeedback(state: SwipeAdjustState, chapters: List<Chapter>) {
    // Remembered so the pill doesn't change shape mid fade-out after the swipe ends.
    var shownKind by remember { mutableStateOf(SwipeKind.Brightness) }
    state.kind?.let { shownKind = it }

    AnimatedVisibility(
        visible = state.kind != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        if (shownKind == SwipeKind.Seek) {
            SeekPill(
                target = state.seekTarget,
                delta = state.seekTarget - state.seekStart,
                duration = state.duration,
                chapter = chapters.chapterAt(state.seekTarget)?.title,
            )
        } else {
            LevelPill(kind = shownKind, level = state.level)
        }
    }
}

@Composable
private fun LevelPill(kind: SwipeKind, level: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MediaColors.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(
                when {
                    kind == SwipeKind.Brightness -> R.drawable.ic_brightness
                    level <= 0f -> R.drawable.ic_volume_off
                    else -> R.drawable.ic_volume_on
                },
            ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(120.dp)
                .height(4.dp)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = radius)
                    drawRoundRect(MediaColors.Accent, size = Size(size.width * level, size.height), cornerRadius = radius)
                },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(level * 100).roundToInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun SeekPill(target: Long, delta: Long, duration: Long, chapter: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MediaColors.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = (if (delta < 0) "−" else "+") + formatTime(abs(delta)),
            color = MediaColors.Accent,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${formatTime(target)} / ${formatTime(duration)}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        if (chapter != null) {
            Text(
                text = chapter,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

/**
 * Overrides the window's brightness with [level] (0–1) while it's non-null, going back to the
 * system brightness once it's null or this leaves the composition.
 */
@Composable
internal fun WindowBrightnessEffect(level: Float?) {
    val window = LocalActivity.current?.window ?: return
    val target = level?.coerceIn(MIN_WINDOW_BRIGHTNESS, 1f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    SideEffect {
        if (window.attributes.screenBrightness != target) {
            window.attributes = window.attributes.apply { screenBrightness = target }
        }
    }
    DisposableEffect(window) {
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
