package com.nameisjayant.composevideos.media.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** Shared timing for the player sliding in and out over the app. */
internal object MediaMotion {
    const val SHEET_DURATION_MS = 380

    // Material 3 "emphasized" curves: a quick start that settles gently, and its mirror.
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enterSpec(): FiniteAnimationSpec<T> = tween(SHEET_DURATION_MS, easing = EmphasizedDecelerate)
    fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(SHEET_DURATION_MS, easing = EmphasizedAccelerate)
}
