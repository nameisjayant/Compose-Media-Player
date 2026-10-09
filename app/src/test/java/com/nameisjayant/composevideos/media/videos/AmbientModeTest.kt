package com.nameisjayant.composevideos.media.videos

import androidx.compose.ui.unit.IntSize
import com.nameisjayant.composevideos.media.videos.presentation.ambientScaleSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientModeTest {

    @Test
    fun `shrinks a preview frame by halves down to the glow width`() {
        assertEquals(
            listOf(IntSize(128, 72), IntSize(64, 36), IntSize(32, 18), IntSize(24, 13)),
            ambientScaleSteps(IntSize(256, 144)),
        )
    }

    @Test
    fun `never shrinks a step by more than half`() {
        var width = 250
        ambientScaleSteps(IntSize(250, 400)).forEach {
            assertTrue(it.width >= width / 2)
            width = it.width
        }
        assertEquals(24, width)
    }

    @Test
    fun `leaves a frame that is already small enough alone`() {
        assertEquals(emptyList<IntSize>(), ambientScaleSteps(IntSize(24, 13)))
        assertEquals(emptyList<IntSize>(), ambientScaleSteps(IntSize(0, 0)))
    }
}
