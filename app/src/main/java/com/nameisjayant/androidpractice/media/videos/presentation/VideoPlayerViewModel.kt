package com.nameisjayant.androidpractice.media.videos.presentation

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
import com.nameisjayant.androidpractice.media.navigation.MediaRoute
import com.nameisjayant.androidpractice.media.videos.data.VideosRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the [player], so rotating between the portrait and full-screen layouts (which recreates
 * the activity) carries on playing from the same spot instead of rebuffering from the start.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VideosRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    // Navigation stores a type-safe route's arguments under their property names.
    private val videoId: String = checkNotNull(savedStateHandle[MediaRoute.VideoPlayer::videoId.name])

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

    /**
     * Whether to (re)start playback the next time the screen is in the foreground: true at first
     * so the video autoplays, then only if it was playing when the app went to the background.
     */
    private var resumeOnForeground = true

    init {
        onIntent(VideoPlayerIntent.LoadVideo)
    }

    fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            VideoPlayerIntent.LoadVideo -> loadVideo()
            is VideoPlayerIntent.PlaybackFailed ->
                _state.update { it.copy(error = "This video can't be played (${intent.reason})") }
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

    private fun loadVideo() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
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
}
