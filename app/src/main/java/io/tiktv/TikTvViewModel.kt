package io.tiktv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tiktv.data.VideoItem
import io.tiktv.data.WatchlistStore
import io.tiktv.network.TiktokFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TikTvViewModel(
    private val fetcher: TiktokFetcher,
    private val watchlist: WatchlistStore,
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val videos: StateFlow<List<VideoItem>> = watchlist.videos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _playing = MutableStateFlow<VideoItem?>(null)
    val playing: StateFlow<VideoItem?> = _playing.asStateFlow()

    fun onInputChanged(value: String) {
        _input.value = value
        _error.value = null
    }

    fun playVideo() {
        val text = _input.value.trim()
        if (text.isEmpty()) {
            _error.value = "Enter a TikTok link first."
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _playing.value = fetcher.fetchVideo(text)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load video."
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveToWatchlist() {
        val text = _input.value.trim()
        if (text.isEmpty()) {
            _error.value = "Enter a TikTok link first."
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                watchlist.add(fetcher.fetchVideo(text))
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load video."
            } finally {
                _loading.value = false
            }
        }
    }

    fun playFromWatchlist(item: VideoItem) {
        _playing.value = item
    }

    fun removeFromWatchlist(item: VideoItem) {
        viewModelScope.launch { watchlist.remove(item) }
    }

    fun clearWatchlist() {
        viewModelScope.launch { watchlist.clear() }
    }

    fun backToHome() {
        _playing.value = null
    }
}
