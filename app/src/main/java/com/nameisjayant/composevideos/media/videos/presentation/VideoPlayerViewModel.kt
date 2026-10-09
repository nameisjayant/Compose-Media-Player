package com.nameisjayant.composevideos.media.videos.presentation

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.nameisjayant.composevideos.media.videos.data.VideosRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the [player] for the whole activity, so the video keeps playing in the floating window
 * while the user moves around the app, and anything that recreates the activity carries on from
 * the same spot instead of rebuffering from the start.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: VideosRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    val player: Player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        // Pause rather than blast audio from the speaker when headphones are unplugged.
        .setHandleAudioBecomingNoisy(true)
        .build()

    /** Whether to (re)start playback the next time the app is in the foreground. */
    private var resumeOnForeground = false

    private var loadJob: Job? = null

    init {
        // Speed and quality carry on from where they were set, even after process death.
        savedStateHandle.get<Float>(KEY_SPEED)?.let(::setPlaybackSpeed)
        savedStateHandle.get<Int>(KEY_QUALITY)?.let(::setQuality)
        // Back after process death: reopen the video and pick playback up once the app is visible.
        savedStateHandle.get<String>(KEY_VIDEO_ID)?.let {
            open(it)
            resumeOnForeground = true
        }
    }

    fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            is VideoPlayerIntent.Open -> {
                open(intent.videoId)
                player.playWhenReady = true
            }
            VideoPlayerIntent.Close -> close()
            VideoPlayerIntent.PlayNext -> skip(1)
            VideoPlayerIntent.PlayPrevious -> skip(-1)
            VideoPlayerIntent.LoadVideo -> loadVideo()
            is VideoPlayerIntent.PlaybackFailed ->
                _state.update { it.copy(error = "This video can't be played (${intent.reason})") }
            is VideoPlayerIntent.SetPlaybackSpeed -> setPlaybackSpeed(intent.speed)
            is VideoPlayerIntent.SetQuality -> setQuality(intent.height)
        }
    }

    fun onForeground() {
        if (resumeOnForeground) player.play()
        resumeOnForeground = false
    }

    /** Not called on rotation, so playback (or a pause) carries straight across. */
    fun onBackground() {
        resumeOnForeground = player.playWhenReady
        player.pause()
    }

    private fun open(videoId: String) {
        val current = _state.value
        savedStateHandle[KEY_VIDEO_ID] = videoId
        _state.update { it.copy(isOpen = true, videoId = videoId, openRequest = it.openRequest + 1) }
        // Reopening the video that's already in the floating window just expands it.
        if (current.isOpen && current.videoId == videoId) return
        _state.update { it.copy(video = null) }
        loadVideo()
    }

    /**
     * Swaps in the video [step] places along the list (wrapping round) where the player already is, so the floating window or
     * picture-in-picture stays put. Unlike [open], it leaves [VideoPlayerState.openRequest] alone.
     */
    private fun skip(step: Int) {
        val currentId = _state.value.videoId ?: return
        viewModelScope.launch {
            val videos = repository.getVideos()
            if (videos.isEmpty()) return@launch
            val next = videos[(videos.indexOfFirst { it.id == currentId } + step).mod(videos.size)]
            // Closed, or another video picked, while the list loaded.
            if (!_state.value.isOpen || _state.value.videoId != currentId) return@launch
            savedStateHandle[KEY_VIDEO_ID] = next.id
            _state.update { it.copy(videoId = next.id, video = null) }
            player.playWhenReady = true
            loadVideo()
        }
    }

    private fun close() {
        loadJob?.cancel()
        savedStateHandle.remove<String>(KEY_VIDEO_ID)
        player.pause()
        player.stop()
        player.clearMediaItems()
        _state.update {
            VideoPlayerState(openRequest = it.openRequest, playbackSpeed = it.playbackSpeed, maxQuality = it.maxQuality)
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        savedStateHandle[KEY_SPEED] = speed
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    /**
     * Caps the video at [height] rather than pinning one track, so the choice carries over to the
     * next video, whose tracks are different. Auto lifts the cap and the player picks the best.
     */
    private fun setQuality(height: Int?) {
        savedStateHandle[KEY_QUALITY] = height
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply { if (height == null) clearVideoSizeConstraints() else setMaxVideoSize(Int.MAX_VALUE, height) }
            .build()
        _state.update { it.copy(maxQuality = height) }
    }

    private fun loadVideo() {
        val videoId = _state.value.videoId ?: return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val video = repository.getVideo(videoId)
                if (video == null) {
                    _state.update { it.copy(isLoading = false, error = "This video isn't available") }
                    return@launch
                }
                if (player.currentMediaItem?.mediaId != video.id) {
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setMediaId(video.id)
                            .setUri(RawResourceDataSource.buildRawResourceUri(video.videoRes))
                            .build(),
                    )
                }
                // Also recovers from a playback error when retrying.
                player.prepare()
                _state.update { it.copy(isLoading = false, video = video) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load this video") }
            }
        }
    }

    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_SPEED = "playbackSpeed"
        const val KEY_QUALITY = "maxQuality"
    }
}
