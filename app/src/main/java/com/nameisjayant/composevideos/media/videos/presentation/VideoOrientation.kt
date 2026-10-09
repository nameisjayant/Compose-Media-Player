package com.nameisjayant.composevideos.media.videos.presentation

import android.app.Activity
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Turns the screen to landscape (full screen) or back to portrait from the full-screen button or
 * Back, whichever way the phone is held. Like YouTube, the screen stays put until the phone is
 * turned to match it, then auto-rotate takes over again, so a later turn still rotates the player.
 */
@Stable
internal class FullScreenController(private val activity: Activity?) {

    /** The orientation the screen is held in (true for landscape), or null while it follows the sensor. */
    var forcedLandscape by mutableStateOf(
        when (activity?.requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> true
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> false
            else -> null
        },
    )
        private set

    fun setFullScreen(fullScreen: Boolean) {
        val activity = activity ?: return
        activity.requestedOrientation = if (fullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        forcedLandscape = fullScreen
    }

    /** Hands rotation back to the sensor. With auto-rotate off the button is the only way to turn, so it keeps hold. */
    fun releaseIfAutoRotate() {
        val activity = activity ?: return
        val autoRotate = Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        if (autoRotate) release()
    }

    fun release() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        forcedLandscape = null
    }
}

@Composable
internal fun rememberFullScreenController(): FullScreenController {
    val activity = LocalActivity.current
    val controller = remember(activity) { FullScreenController(activity) }
    val forcedLandscape = controller.forcedLandscape

    // Watches how the phone is held while the screen is forced, to let go once they agree.
    DisposableEffect(activity, forcedLandscape) {
        if (activity == null || forcedLandscape == null) return@DisposableEffect onDispose {}
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                // Well inside each quarter, so a phone tilted half way doesn't count.
                val heldLandscape = degrees in 60..120 || degrees in 240..300
                val heldPortrait = degrees <= 30 || degrees >= 330 || degrees in 150..210
                if (if (forcedLandscape) heldLandscape else heldPortrait) controller.releaseIfAutoRotate()
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    // Closing the player gives the rest of the app its normal rotation back; recreating the
    // activity (e.g. a theme change) keeps the hold, which the new composition picks up.
    DisposableEffect(controller) {
        onDispose {
            if (controller.forcedLandscape != null && activity?.isChangingConfigurations != true) controller.release()
        }
    }
    return controller
}
