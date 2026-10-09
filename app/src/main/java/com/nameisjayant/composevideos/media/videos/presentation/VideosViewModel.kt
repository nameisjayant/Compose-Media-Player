package com.nameisjayant.composevideos.media.videos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nameisjayant.composevideos.media.videos.data.VideosRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VideosViewModel @Inject constructor(
    private val repository: VideosRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideosState())
    val state: StateFlow<VideosState> = _state.asStateFlow()

    init {
        onIntent(VideosIntent.LoadVideos)
    }

    fun onIntent(intent: VideosIntent) {
        when (intent) {
            VideosIntent.LoadVideos -> loadVideos()
        }
    }

    private fun loadVideos() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val videos = repository.getVideos()
                _state.update { it.copy(isLoading = false, videos = videos) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load videos") }
            }
        }
    }
}
