package com.nameisjayant.composevideos.media.videos

import com.nameisjayant.composevideos.media.videos.data.BundledVideos
import com.nameisjayant.composevideos.media.videos.data.Chapter
import com.nameisjayant.composevideos.media.videos.data.chapterAt
import com.nameisjayant.composevideos.media.videos.data.findTimestamps
import com.nameisjayant.composevideos.media.videos.data.parseChapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersTest {

    @Test
    fun `reads chapters from lines starting with a time`() {
        val chapters = parseChapters(
            """
            A short film.

            0:00 Intro
            0:15 - The chase
            1:02:03 · Credits
            """.trimIndent(),
        )
        assertEquals(
            listOf(Chapter(0, "Intro"), Chapter(15_000, "The chase"), Chapter(3_723_000, "Credits")),
            chapters,
        )
    }

    @Test
    fun `no chapters unless the first starts at zero`() {
        assertTrue(parseChapters("0:05 A\n0:20 B\n0:40 C").isEmpty())
    }

    @Test
    fun `no chapters with fewer than three`() {
        assertTrue(parseChapters("0:00 A\n0:20 B").isEmpty())
    }

    @Test
    fun `no chapters when one is shorter than ten seconds or out of order`() {
        assertTrue(parseChapters("0:00 A\n0:05 B\n0:40 C").isEmpty())
        assertTrue(parseChapters("0:00 A\n0:40 B\n0:20 C").isEmpty())
    }

    @Test
    fun `times mid-sentence are links but not chapters`() {
        val text = "Watch from 0:30, or 12:5 and 1:75:00 aren't times."
        val timestamps = findTimestamps(text)
        assertEquals(listOf(30_000L), timestamps.map { it.positionMs })
        assertEquals("0:30", text.substring(timestamps.single().range))
        assertTrue(parseChapters(text).isEmpty())
    }

    @Test
    fun `chapter at a position is the last one started`() {
        val chapters = listOf(Chapter(0, "A"), Chapter(10_000, "B"), Chapter(30_000, "C"))
        assertEquals("A", chapters.chapterAt(9_999)?.title)
        assertEquals("B", chapters.chapterAt(10_000)?.title)
        assertEquals("C", chapters.chapterAt(60_000)?.title)
        assertNull(emptyList<Chapter>().chapterAt(0))
    }

    @Test
    fun `every bundled video has chapters`() {
        BundledVideos.all.forEach { assertTrue(it.id, it.chapters.size >= 3) }
    }
}
