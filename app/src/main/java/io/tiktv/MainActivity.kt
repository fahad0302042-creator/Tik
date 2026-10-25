package io.tiktv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.tiktv.data.WatchlistStore
import io.tiktv.network.TiktokFetcher
import io.tiktv.ui.theme.TikTvTheme
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TikTvTheme {
                val viewModel: TikTvViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val context = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                            TikTvViewModel(
                                fetcher = TiktokFetcher(OkHttpClient()),
                                watchlist = WatchlistStore(context),
                            )
                        }
                    },
                )
                val playing by viewModel.playing.collectAsStateWithLifecycle()
                TikTvApp(viewModel = viewModel, playing = playing)
            }
        }
    }
}
