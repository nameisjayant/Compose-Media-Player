package com.nameisjayant.composevideos.media.videos

import com.nameisjayant.composevideos.media.videos.data.nearestFrame
import com.nameisjayant.composevideos.media.videos.data.previewOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekPreviewsTest {

    @Test
    fun `decodes every eighth frame first, then fills the gaps in`() {
        assertEquals(
            listOf(0, 8, 16, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15),
            previewOrder(17),
        )
    }

    @Test
    fun `decodes every frame exactly once`() {
        for (count in listOf(0, 1, 7, 31, 60)) {
            assertEquals((0 until count).toList(), previewOrder(count).sorted())
        }
    }

    @Test
    fun `shows the closest decoded frame, the earlier one on a tie`() {
        val frames = listOf("a", null, null, null, "e", null)
        assertEquals("a", nearestFrame(frames, 0))
        assertEquals("a", nearestFrame(frames, 1))
        assertEquals("a", nearestFrame(frames, 2))
        assertEquals("e", nearestFrame(frames, 3))
        assertEquals("e", nearestFrame(frames, 5))
    }

    @Test
    fun `clamps positions off either end`() {
        val frames = listOf("a", null, "c")
        assertEquals("a", nearestFrame(frames, -4))
        assertEquals("c", nearestFrame(frames, 9))
    }

    @Test
    fun `has nothing to show before any frame decodes`() {
        assertNull(nearestFrame(listOf<String?>(null, null), 1))
        assertNull(nearestFrame(emptyList<String?>(), 0))
    }
}
