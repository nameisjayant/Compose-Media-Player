package com.nameisjayant.composevideos.media.reels.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nameisjayant.composevideos.media.reels.data.CurrentUser
import com.nameisjayant.composevideos.media.reels.data.Reel
import com.nameisjayant.composevideos.media.reels.data.ReelComment
import com.nameisjayant.composevideos.media.reels.data.ReelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val repository: ReelsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReelsState())
    val state: StateFlow<ReelsState> = _state.asStateFlow()

    private val _effects = Channel<ReelsEffect>(Channel.BUFFERED)
    val effects: Flow<ReelsEffect> = _effects.receiveAsFlow()

    /** Reels dropped via Not interested or Report; kept out of the feed across reloads. */
    private val hiddenReelIds = mutableSetOf<String>()

    /** The last Not interested reel and where it sat, so Undo can put it back. */
    private var lastNotInterested: Pair<Int, Reel>? = null

    init {
        onIntent(ReelsIntent.LoadReels)
    }

    fun onIntent(intent: ReelsIntent) {
        when (intent) {
            ReelsIntent.LoadReels -> loadReels()
            is ReelsIntent.PageSettled -> _state.update {
                // A newly settled reel always starts playing, like Instagram.
                if (it.currentIndex == intent.index) it
                else it.copy(currentIndex = intent.index, isPaused = false, hold = null)
            }
            ReelsIntent.TogglePlayPause -> _state.update { it.copy(isPaused = !it.isPaused) }
            ReelsIntent.ToggleMute -> _state.update { it.copy(isMuted = !it.isMuted) }
            ReelsIntent.CycleSpeed -> _state.update {
                val next = (PlaybackSpeeds.indexOf(it.playbackSpeed) + 1) % PlaybackSpeeds.size
                it.copy(playbackSpeed = PlaybackSpeeds[next])
            }
            ReelsIntent.ToggleAutoScroll -> {
                _state.update { it.copy(autoScroll = !it.autoScroll) }
                // The icon alone doesn't say what changed; spell it out.
                val message = if (_state.value.autoScroll) "Auto-scroll on" else "Auto-scroll off, reels loop"
                viewModelScope.launch { _effects.send(ReelsEffect.ShowMessage(message)) }
            }
            is ReelsIntent.HoldStarted -> _state.update { it.copy(hold = intent.hold) }
            ReelsIntent.HoldReleased -> _state.update { it.copy(hold = null) }
            is ReelsIntent.PlaybackFailed -> viewModelScope.launch {
                val title = _state.value.reels.firstOrNull { it.id == intent.reelId }?.title ?: "This reel"
                _effects.send(ReelsEffect.ShowMessage("$title can't be played (${intent.reason})"))
            }
            is ReelsIntent.ToggleLike -> _state.update {
                val liked = if (intent.reelId in it.likedReelIds) it.likedReelIds - intent.reelId
                else it.likedReelIds + intent.reelId
                it.copy(likedReelIds = liked)
            }
            is ReelsIntent.DoubleTapLike -> _state.update { it.copy(likedReelIds = it.likedReelIds + intent.reelId) }
            is ReelsIntent.OpenComments -> _state.update { it.copy(commentsReelId = intent.reelId) }
            ReelsIntent.CloseComments -> _state.update { it.copy(commentsReelId = null) }
            is ReelsIntent.PostComment -> postComment(intent.reelId, intent.text, intent.parentId)
            is ReelsIntent.ToggleCommentLike -> _state.update {
                val liked = if (intent.commentId in it.likedCommentIds) it.likedCommentIds - intent.commentId
                else it.likedCommentIds + intent.commentId
                it.copy(likedCommentIds = liked)
            }
            is ReelsIntent.DeleteComment -> deleteComment(intent.reelId, intent.commentId)
            is ReelsIntent.OpenOptions -> _state.update { it.copy(optionsReelId = intent.reelId) }
            ReelsIntent.CloseOptions -> _state.update { it.copy(optionsReelId = null) }
            is ReelsIntent.NotInterested -> hideReel(intent.reelId)?.let { removed ->
                lastNotInterested = removed
                showMessage(ReelsEffect.ShowMessage("You'll see fewer reels like this", "Undo", ReelsIntent.UndoNotInterested))
            }
            is ReelsIntent.Report -> hideReel(intent.reelId)?.let {
                if (lastNotInterested?.second?.id == intent.reelId) lastNotInterested = null
                showMessage(ReelsEffect.ShowMessage("Thanks for reporting. We'll review it for ${intent.reason.lowercase()}."))
            }
            ReelsIntent.UndoNotInterested -> undoNotInterested()
            is ReelsIntent.Share -> share(intent.reelId)
        }
    }

    private fun postComment(reelId: String, text: String, parentId: String?) {
        val body = text.trim()
        if (body.isEmpty()) return
        val comment = ReelComment(
            id = "${reelId}_c${System.nanoTime()}",
            author = CurrentUser,
            text = body,
            postedAgo = "now",
        )
        updateComments(reelId) { comments ->
            if (parentId == null) return@updateComments comments + comment
            // Replying to a reply still lands in the top-level comment's thread.
            comments.map { top ->
                if (top.id == parentId || top.replies.any { it.id == parentId }) top.copy(replies = top.replies + comment)
                else top
            }
        }
    }

    private fun deleteComment(reelId: String, commentId: String) {
        val comments = _state.value.reels.firstOrNull { it.id == reelId }?.comments ?: return
        val target = comments.flatMap { listOf(it) + it.replies }.firstOrNull { it.id == commentId } ?: return
        if (!target.isMine) return
        val goneIds = setOf(commentId) + target.replies.map { it.id }
        updateComments(reelId) { all ->
            all.filter { it.id != commentId }.map { top -> top.copy(replies = top.replies.filter { it.id != commentId }) }
        }
        _state.update { it.copy(likedCommentIds = it.likedCommentIds - goneIds) }
    }

    private fun updateComments(reelId: String, transform: (List<ReelComment>) -> List<ReelComment>) {
        _state.update { state ->
            state.copy(reels = state.reels.map { if (it.id == reelId) it.copy(comments = transform(it.comments)) else it })
        }
    }

    /** Takes the reel out of the feed; the one after it slides into its place. Returns it and its old index. */
    private fun hideReel(reelId: String): Pair<Int, Reel>? {
        val state = _state.value
        val index = state.reels.indexOfFirst { it.id == reelId }
        if (index < 0) return null
        hiddenReelIds += reelId
        val reels = state.reels - state.reels[index]
        _state.update {
            it.copy(
                reels = reels,
                currentIndex = when {
                    index < it.currentIndex -> it.currentIndex - 1
                    else -> it.currentIndex.coerceAtMost((reels.size - 1).coerceAtLeast(0))
                },
                // Whatever slides in starts fresh, like a newly settled page.
                isPaused = if (index == it.currentIndex) false else it.isPaused,
                hold = null,
                optionsReelId = null,
            )
        }
        return index to state.reels[index]
    }

    private fun undoNotInterested() {
        val (index, reel) = lastNotInterested ?: return
        lastNotInterested = null
        hiddenReelIds -= reel.id
        _state.update {
            val at = index.coerceAtMost(it.reels.size)
            it.copy(
                reels = it.reels.toMutableList().apply { add(at, reel) },
                // Land back on the restored reel rather than the one that replaced it.
                currentIndex = at,
                isPaused = false,
            )
        }
    }

    private fun showMessage(effect: ReelsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun share(reelId: String) {
        val reel = _state.value.reels.firstOrNull { it.id == reelId } ?: return
        _state.update { state ->
            state.copy(reels = state.reels.map { if (it.id == reelId) it.copy(shareCount = it.shareCount + 1) else it })
        }
        viewModelScope.launch {
            _effects.send(ReelsEffect.ShareReel("${reel.title} — by ${reel.channel} (CC BY). Watch it on Reels!"))
        }
    }

    private fun loadReels() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val reels = repository.getReels()
                _state.update {
                    it.copy(isLoading = false, reels = reels.filter { r -> r.id !in hiddenReelIds }, currentIndex = 0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load reels") }
            }
        }
    }
}
