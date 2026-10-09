package com.nameisjayant.composevideos.media.reels

import com.nameisjayant.composevideos.media.reels.data.Reel
import com.nameisjayant.composevideos.media.reels.data.ReelComment
import com.nameisjayant.composevideos.media.reels.data.commentCount
import com.nameisjayant.composevideos.media.reels.data.ReelsRepository
import com.nameisjayant.composevideos.media.reels.presentation.ReelHold
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
        Reel(
            "b", "B", "Chan", videoRes = 0,
            comments = listOf(ReelComment("b_c0", "kiri", "hi", "1h", likeCount = 2)),
        ),
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
    fun `hold applies until released and leaves pause state alone`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.HoldStarted(ReelHold.Pause))
        assertEquals(ReelHold.Pause, vm.state.value.hold)
        vm.onIntent(ReelsIntent.HoldReleased)
        assertNull(vm.state.value.hold)
        assertFalse(vm.state.value.isPaused)

        vm.onIntent(ReelsIntent.HoldStarted(ReelHold.FastForward))
        assertEquals(ReelHold.FastForward, vm.state.value.hold)
        assertEquals(1f, vm.state.value.playbackSpeed)
    }

    @Test
    fun `settling on a new page drops a hold`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.HoldStarted(ReelHold.FastForward))
        vm.onIntent(ReelsIntent.PageSettled(1))
        assertNull(vm.state.value.hold)
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
        assertEquals(listOf("hi", "nice"), comments.map { it.text })

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

    @Test
    fun `replies join the top-level thread, even when replying to a reply`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.PostComment("b", "first", parentId = "b_c0"))
        val replyId = vm.state.value.reels.first { it.id == "b" }.comments.single().replies.single().id
        vm.onIntent(ReelsIntent.PostComment("b", "second", parentId = replyId))

        val reel = vm.state.value.reels.first { it.id == "b" }
        assertEquals(listOf("first", "second"), reel.comments.single().replies.map { it.text })
        assertEquals(3, reel.commentCount)
    }

    @Test
    fun `comment like toggles`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.ToggleCommentLike("b_c0"))
        assertTrue("b_c0" in vm.state.value.likedCommentIds)
        vm.onIntent(ReelsIntent.ToggleCommentLike("b_c0"))
        assertFalse("b_c0" in vm.state.value.likedCommentIds)
    }

    @Test
    fun `only your own comments can be deleted`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.DeleteComment("b", "b_c0"))
        assertEquals(1, vm.state.value.reels.first { it.id == "b" }.comments.size)

        vm.onIntent(ReelsIntent.PostComment("b", "mine"))
        val mine = vm.state.value.reels.first { it.id == "b" }.comments.last()
        vm.onIntent(ReelsIntent.PostComment("b", "reply to mine", parentId = mine.id))
        vm.onIntent(ReelsIntent.ToggleCommentLike(mine.id))
        vm.onIntent(ReelsIntent.DeleteComment("b", mine.id))

        val reel = vm.state.value.reels.first { it.id == "b" }
        assertEquals(listOf("b_c0"), reel.comments.map { it.id })
        assertFalse(mine.id in vm.state.value.likedCommentIds)
    }

    @Test
    fun `not interested hides the reel and undo puts it back`() = runTest {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.PageSettled(1))
        vm.onIntent(ReelsIntent.OpenOptions("b"))
        vm.onIntent(ReelsIntent.NotInterested("b"))

        assertEquals(listOf("a", "c"), vm.state.value.reels.map { it.id })
        assertEquals(1, vm.state.value.currentIndex)
        assertNull(vm.state.value.optionsReelId)
        val effect = vm.effects.first() as ReelsEffect.ShowMessage
        assertEquals(ReelsIntent.UndoNotInterested, effect.action)

        vm.onIntent(ReelsIntent.UndoNotInterested)
        assertEquals(listOf("a", "b", "c"), vm.state.value.reels.map { it.id })
        assertEquals(1, vm.state.value.currentIndex)
    }

    @Test
    fun `reported reels stay hidden across reloads`() {
        val vm = ReelsViewModel(FakeRepository { reels })
        vm.onIntent(ReelsIntent.PageSettled(2))
        vm.onIntent(ReelsIntent.Report("c", "Spam"))
        assertEquals(listOf("a", "b"), vm.state.value.reels.map { it.id })
        assertEquals(1, vm.state.value.currentIndex)

        vm.onIntent(ReelsIntent.UndoNotInterested)
        vm.onIntent(ReelsIntent.LoadReels)
        assertEquals(listOf("a", "b"), vm.state.value.reels.map { it.id })
    }
}
