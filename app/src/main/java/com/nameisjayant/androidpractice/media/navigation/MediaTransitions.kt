package com.nameisjayant.androidpractice.media.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset

/** Shared timing for the player sheet and the tab bar, so they move as one. */
internal object MediaMotion {
    const val SHEET_DURATION_MS = 380

    // Material 3 "emphasized" curves: a quick start that settles gently, and its mirror.
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enterSpec(): FiniteAnimationSpec<T> = tween(SHEET_DURATION_MS, easing = EmphasizedDecelerate)
    fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(SHEET_DURATION_MS, easing = EmphasizedAccelerate)

    /** Slides up from the bottom edge. */
    val slideUp: EnterTransition =
        slideInVertically(enterSpec<IntOffset>()) { it } + fadeIn(enterSpec())

    /** Slides back down off the bottom edge; also what the predictive-back gesture scrubs. */
    val slideDown: ExitTransition =
        slideOutVertically(exitSpec<IntOffset>()) { it } + fadeOut(exitSpec())
}

/**
 * What stays underneath the player: held in place (only the player moves) and dimmed slightly
 * for depth, then brightened back when it's uncovered.
 */
private const val UNDERLAY_ALPHA = 0.6f
internal val HoldBelow: ExitTransition = fadeOut(MediaMotion.enterSpec(), targetAlpha = UNDERLAY_ALPHA)
internal val RevealBelow: EnterTransition = fadeIn(MediaMotion.exitSpec(), initialAlpha = UNDERLAY_ALPHA)
