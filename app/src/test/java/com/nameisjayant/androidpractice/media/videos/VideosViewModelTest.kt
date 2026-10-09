package com.nameisjayant.androidpractice.media.videos

import com.nameisjayant.androidpractice.media.videos.data.Video
import com.nameisjayant.androidpractice.media.videos.data.VideosRepository
import com.nameisjayant.androidpractice.media.videos.presentation.VideosIntent
import com.nameisjayant.androidpractice.media.videos.presentation.VideosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideosViewModelTest {

    private class FakeRepository(var result: () -> List<Video>) : VideosRepository {
        override suspend fun getVideos(): List<Video> = result()
        override suspend fun getVideo(id: String): Video? = result().firstOrNull { it.id == id }
    }

    private val videos = listOf(
        Video("a", "A", "Chan", "About A", "1:00", "2008", videoRes = 0, thumbnailRes = 0),
        Video("b", "B", "Chan", "About B", "1:00", "2010", videoRes = 0, thumbnailRes = 0),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads videos on init`() {
        val state = VideosViewModel(FakeRepository { videos }).state.value
        assertFalse(state.isLoading)
        assertEquals(videos, state.videos)
        assertNull(state.error)
    }

    @Test
    fun `load failure shows error and retry recovers`() {
        val repo = FakeRepository { error("disk") }
        val vm = VideosViewModel(repo)
        assertEquals("disk", vm.state.value.error)

        repo.result = { videos }
        vm.onIntent(VideosIntent.LoadVideos)
        assertNull(vm.state.value.error)
        assertEquals(videos, vm.state.value.videos)
    }
}
