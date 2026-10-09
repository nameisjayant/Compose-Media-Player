package com.nameisjayant.composevideos.media.reels

import com.nameisjayant.composevideos.media.reels.data.Reel
import com.nameisjayant.composevideos.media.reels.data.ReelsRepository
import com.nameisjayant.composevideos.media.reels.presentation.ReelsEffect
import com.nameisjayant.composevideos.media.reels.presentation.ReelsIntent
import com.nameisjayant.composevideos.media.reels.presentation.ReelsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsViewModelTest {

    private class FakeRepository(var result: () -> List<Reel>) : ReelsRepository {
        override suspend fun getReels(): List<Reel> = result()
    }

    private val reels = listOf(
        Reel("a", "A", "Chan", videoRes = 0, likeCount = 10, shareCount = 3),
        Reel("b", "B", "Chan", videoRes = 0),
        Reel("c", "C", "Chan", videoRes = 0),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads reels on init`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(reels, state.reels)
        assertEquals(0, state.currentIndex)
        assertNull(state.error)
    }

    @Test
    fun `load failure shows error and retry recovers`() {
        val repo = FakeRepository { error("offline") }
        val vm = ReelsViewModel(repo)
        assertEquals("offline", vm.state.value.error)

        repo.result = { reels }
        vm.onIntent(ReelsIntent.LoadReels)
        assertNull(vm.state.value.error)
        assertEquals(reels, vm.state.value.reels)
    }

    @Test
    fun `settling on a new page resumes playback`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.TogglePlayPause)
        assertTrue(vm.state.value.isPaused)

        vm.onIntent(ReelsIntent.PageSettled(2))
        assertEquals(2, vm.state.value.currentIndex)
        assertFalse(vm.state.value.isPaused)
    }

    @Test
    fun `settling on the same page keeps pause state`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.TogglePlayPause)
        vm.onIntent(ReelsIntent.PageSettled(0))
        assertTrue(vm.state.value.isPaused)
    }

    @Test
    fun `mute toggles`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.ToggleMute)
        assertTrue(vm.state.value.isMuted)
        vm.onIntent(ReelsIntent.ToggleMute)
        assertFalse(vm.state.value.isMuted)
    }

    @Test
    fun `playback failure emits a message naming the reel`() = runTest {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.PlaybackFailed("b", "video not found"))
        val effect = vm.effects.first()
        assertEquals(ReelsEffect.ShowMessage("B can't be played (video not found)"), effect)
    }

    @Test
    fun `like toggles but double-tap only ever likes`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.ToggleLike("a"))
        assertTrue("a" in vm.state.value.likedReelIds)
        vm.onIntent(ReelsIntent.DoubleTapLike("a"))
        assertTrue("a" in vm.state.value.likedReelIds)
        vm.onIntent(ReelsIntent.ToggleLike("a"))
        assertFalse("a" in vm.state.value.likedReelIds)
    }

    @Test
    fun `posting a comment appends it and ignores blanks`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.OpenComments("b"))
        assertEquals("b", vm.state.value.commentsReelId)

        vm.onIntent(ReelsIntent.PostComment("b", "   "))
        vm.onIntent(ReelsIntent.PostComment("b", "  nice  "))
        val comments = vm.state.value.reels.first { it.id == "b" }.comments
        assertEquals(listOf("nice"), comments.map { it.text })

        vm.onIntent(ReelsIntent.CloseComments)
        assertNull(vm.state.value.commentsReelId)
    }

    @Test
    fun `share bumps the count and opens the share sheet`() = runTest {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.Share("a"))
        assertEquals(4, vm.state.value.reels.first { it.id == "a" }.shareCount)
        assertTrue(vm.effects.first() is ReelsEffect.ShareReel)
    }
}
