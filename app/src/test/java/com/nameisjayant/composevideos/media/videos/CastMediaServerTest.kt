package com.nameisjayant.composevideos.media.videos

import com.nameisjayant.composevideos.media.videos.data.HttpRequest
import com.nameisjayant.composevideos.media.videos.data.parseRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastMediaServerTest {

    @Test
    fun `serves a closed range as asked`() {
        assertEquals(0L..1023L, parseRange("bytes=0-1023", 5_000))
    }

    @Test
    fun `serves an open-ended range to the end of the file`() {
        assertEquals(4_000L..4_999L, parseRange("bytes=4000-", 5_000))
    }

    @Test
    fun `serves a suffix range from the end`() {
        assertEquals(4_500L..4_999L, parseRange("bytes=-500", 5_000))
        assertEquals(0L..4_999L, parseRange("bytes=-9000", 5_000))
    }

    @Test
    fun `clamps a range that runs past the end`() {
        assertEquals(100L..4_999L, parseRange("bytes=100-99999", 5_000))
    }

    @Test
    fun `refuses ranges it can't meet`() {
        assertNull(parseRange("bytes=5000-", 5_000))
        assertNull(parseRange("bytes=20-10", 5_000))
        assertNull(parseRange("bytes=0-10,20-30", 5_000))
        assertNull(parseRange("items=0-10", 5_000))
        assertNull(parseRange("bytes=-0", 5_000))
    }

    @Test
    fun `reads the method, path and range of a request`() {
        val raw = "GET /video/bbb.mp4?t=1 HTTP/1.1\r\nHost: 192.168.1.5\r\nrange: bytes=10-\r\n\r\n"
        val request = HttpRequest.read(raw.byteInputStream())!!
        assertEquals("GET", request.method)
        assertEquals("/video/bbb.mp4", request.path)
        assertEquals("bytes=10-", request.range)
    }
}
