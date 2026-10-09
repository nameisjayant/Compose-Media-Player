package com.nameisjayant.androidpractice.media.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Cinematic palette: near-black canvas, soft whites and a single champagne accent. */
object MediaColors {
    val Canvas = Color(0xFF0A0A0C)
    val Surface = Color(0xFF15151A)
    val SurfaceRaised = Color(0xFF1E1E25)
    val Accent = Color(0xFFE9C46A)
    val AccentDeep = Color(0xFFB8893B)
    val OnCanvas = Color(0xFFF5F2EC)
    val Muted = Color(0xFFF5F2EC).copy(alpha = 0.6f)
    val Hairline = Color.White.copy(alpha = 0.12f)
    val Glass = Color(0xFF121216).copy(alpha = 0.72f)
}

private val MediaColorScheme = darkColorScheme(
    primary = MediaColors.Accent,
    onPrimary = MediaColors.Canvas,
    secondary = MediaColors.AccentDeep,
    background = MediaColors.Canvas,
    onBackground = MediaColors.OnCanvas,
    surface = MediaColors.Surface,
    onSurface = MediaColors.OnCanvas,
    surfaceVariant = MediaColors.SurfaceRaised,
    onSurfaceVariant = MediaColors.Muted,
    outline = MediaColors.Hairline,
    inverseSurface = MediaColors.OnCanvas,
    inverseOnSurface = MediaColors.Canvas,
)

private val MediaTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    )
}

/** The media experience is always dark, whatever the system theme, so video stays the hero. */
@Composable
fun MediaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MediaColorScheme,
        typography = MediaTypography,
        content = content,
    )
}
