package com.nameisjayant.androidpractice.media.reels.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nameisjayant.androidpractice.media.reels.data.ReelComment
import com.nameisjayant.androidpractice.media.reels.data.ReelsRepository
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

    init {
        onIntent(ReelsIntent.LoadReels)
    }

    fun onIntent(intent: ReelsIntent) {
        when (intent) {
            ReelsIntent.LoadReels -> loadReels()
            is ReelsIntent.PageSettled -> _state.update {
                // A newly settled reel always starts playing, like Instagram.
                if (it.currentIndex == intent.index) it
                else it.copy(currentIndex = intent.index, isPaused = false)
            }
            ReelsIntent.TogglePlayPause -> _state.update { it.copy(isPaused = !it.isPaused) }
            ReelsIntent.ToggleMute -> _state.update { it.copy(isMuted = !it.isMuted) }
            ReelsIntent.CycleSpeed -> _state.update {
                val next = (PlaybackSpeeds.indexOf(it.playbackSpeed) + 1) % PlaybackSpeeds.size
                it.copy(playbackSpeed = PlaybackSpeeds[next])
            }
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
            is ReelsIntent.PostComment -> postComment(intent.reelId, intent.text)
            is ReelsIntent.Share -> share(intent.reelId)
        }
    }

    private fun postComment(reelId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        _state.update { state ->
            state.copy(
                reels = state.reels.map { reel ->
                    if (reel.id != reelId) reel
                    else reel.copy(
                        comments = reel.comments + ReelComment(
                            id = "${reelId}_c${System.nanoTime()}",
                            author = "you",
                            text = body,
                            postedAgo = "now",
                        ),
                    )
                },
            )
        }
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
                _state.update { it.copy(isLoading = false, reels = reels, currentIndex = 0) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load reels") }
            }
        }
    }
}
