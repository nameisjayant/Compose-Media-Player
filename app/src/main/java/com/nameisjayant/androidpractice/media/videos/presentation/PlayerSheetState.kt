package com.nameisjayant.androidpractice.media.videos.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** Where the floating player docks when it's shrunk down. */
enum class MiniPlayerCorner {
    TopLeft, TopRight, BottomLeft, BottomRight;

    val isTop: Boolean get() = this == TopLeft || this == TopRight
    val isLeft: Boolean get() = this == TopLeft || this == BottomLeft

    /** The bottom corner on the same side; swiping the player down always lands at the bottom. */
    fun toBottom(): MiniPlayerCorner = if (isLeft) BottomLeft else BottomRight
}

/**
 * How the player sits over the app: full screen, shrunk into the floating window, or somewhere in
 * between while the finger drags it. Hoisted to the activity so the tab bar can move with it.
 */
@Stable
class PlayerSheetState internal constructor(
    collapsed: Float,
    offscreen: Float,
    corner: MiniPlayerCorner,
    handledRequest: Int,
) {
    /** 0 = full player, 1 = floating window. */
    val collapse = Animatable(collapsed).apply { updateBounds(0f, 1f) }

    /** 0 = on screen, 1 = slid away (opening slides it in, closing slides it out). */
    val offscreen = Animatable(offscreen).apply { updateBounds(0f, 1f) }

    /** How far the floating window has been dragged from its corner. */
    val miniDrag = Animatable(Offset.Zero, Offset.VectorConverter)

    var corner by mutableStateOf(corner)

    /** The last [VideoPlayerState.openRequest] the UI has animated for. */
    internal var handledRequest by mutableIntStateOf(handledRequest)

    /** How far the tab bar is tucked away: fully under the full player, not at all otherwise. */
    val tabBarHidden: Float get() = (1f - collapse.value) * (1f - offscreen.value)

    companion object {
        val Saver = listSaver<PlayerSheetState, Any>(
            // Mid-gesture positions settle to the nearest end, so it never restores half dragged.
            save = {
                listOf(
                    if (it.collapse.targetValue >= 0.5f) 1f else 0f,
                    if (it.offscreen.targetValue >= 0.5f) 1f else 0f,
                    it.corner.ordinal,
                    it.handledRequest,
                )
            },
            restore = {
                PlayerSheetState(
                    collapsed = it[0] as Float,
                    offscreen = it[1] as Float,
                    corner = MiniPlayerCorner.entries[it[2] as Int],
                    handledRequest = it[3] as Int,
                )
            },
        )
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState = rememberSaveable(saver = PlayerSheetState.Saver) {
    PlayerSheetState(
        collapsed = 0f,
        offscreen = 1f,
        corner = MiniPlayerCorner.BottomRight,
        handledRequest = 0,
    )
}
