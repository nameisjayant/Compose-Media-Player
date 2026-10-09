package com.nameisjayant.androidpractice.media.videos.presentation

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.nameisjayant.androidpractice.R
import kotlin.math.roundToInt

/** The range of aspect ratios the system accepts for a picture-in-picture window. */
private val MinPipRatio = Rational(100, 239)
private val MaxPipRatio = Rational(239, 100)

/** Whether the activity is currently shrunk into a picture-in-picture window. */
@Composable
fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPip by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/**
 * Lets the video carry on in a floating window when the user leaves the app (home button or
 * swipe up) while it's playing. On Android 12+ the system shrinks it straight from the gesture;
 * earlier versions enter picture-in-picture from [ComponentActivity.onUserLeaveHint].
 *
 * The window shows a play/pause button that calls [onPlayPause]. Leaving the player screen turns
 * auto-entry off again, so the rest of the app backgrounds normally.
 *
 * @param videoAspectRatio width / height of the video, used to shape the window.
 * @param videoBounds where the video sits in the window, so the shrink animates from it.
 */
@Composable
fun PictureInPictureEffect(
    autoEnter: Boolean,
    isPlaying: Boolean,
    videoAspectRatio: Float,
    videoBounds: Rect?,
    onPlayPause: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val supported = remember(activity) {
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    if (!supported) return

    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    val playPauseAction = "${activity.packageName}.action.PIP_PLAY_PAUSE"

    // Taps on the window's play/pause button arrive as a broadcast.
    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == playPauseAction) currentOnPlayPause()
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(playPauseAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { activity.unregisterReceiver(receiver) }
    }

    val ratio = videoAspectRatio.toPipRatio()
    val params = remember(isPlaying, autoEnter, ratio, videoBounds) {
        buildParams(activity, playPauseAction, isPlaying, autoEnter, ratio, videoBounds)
    }
    val currentParams by rememberUpdatedState(params)
    val currentAutoEnter by rememberUpdatedState(autoEnter)

    DisposableEffect(activity, params) {
        activity.setPictureInPictureParams(params)
        onDispose { }
    }

    DisposableEffect(activity) {
        // Android 12+ handles this itself through setAutoEnterEnabled.
        val onUserLeave = Runnable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && currentAutoEnter) {
                try {
                    activity.enterPictureInPictureMode(currentParams)
                } catch (_: IllegalStateException) {
                    // The user turned picture-in-picture off for this app in Settings.
                }
            }
        }
        activity.addOnUserLeaveHintListener(onUserLeave)
        onDispose {
            activity.removeOnUserLeaveHintListener(onUserLeave)
            // Off the player screen, going home should just background the app.
            val reset = PictureInPictureParams.Builder().setActions(emptyList())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) reset.setAutoEnterEnabled(false)
            activity.setPictureInPictureParams(reset.build())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun buildParams(
    context: Context,
    playPauseAction: String,
    isPlaying: Boolean,
    autoEnter: Boolean,
    ratio: Rational,
    videoBounds: Rect?,
): PictureInPictureParams {
    val label = if (isPlaying) "Pause" else "Play"
    val action = RemoteAction(
        Icon.createWithResource(context, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
        label,
        label,
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(playPauseAction).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(ratio)
        .setActions(listOf(action))
    videoBounds?.fitTo(ratio)?.let(builder::setSourceRectHint)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        // Video content shouldn't cross-fade while the window is resized.
        builder.setSeamlessResizeEnabled(false)
    }
    return builder.build()
}

/** The ratio as a [Rational] within the range the system allows, falling back to 16:9. */
private fun Float.toPipRatio(): Rational {
    if (!isFinite() || this <= 0f) return Rational(16, 9)
    val ratio = Rational((this * 1000).roundToInt(), 1000)
    return when {
        ratio < MinPipRatio -> MinPipRatio
        ratio > MaxPipRatio -> MaxPipRatio
        else -> ratio
    }
}

/**
 * The part of these bounds the video actually fills (it's letterboxed in full screen), so the
 * window grows out of the picture rather than the black bars.
 */
private fun Rect.fitTo(ratio: Rational): android.graphics.Rect? {
    if (width <= 0f || height <= 0f) return null
    val target = ratio.toFloat()
    val (w, h) = if (width / height > target) height * target to height else width to width / target
    val left = this.left + (width - w) / 2
    val top = this.top + (height - h) / 2
    return android.graphics.Rect(left.roundToInt(), top.roundToInt(), (left + w).roundToInt(), (top + h).roundToInt())
}
