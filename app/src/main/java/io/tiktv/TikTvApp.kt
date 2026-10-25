package io.tiktv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.tiktv.data.VideoItem
import io.tiktv.ui.home.HomeScreen
import io.tiktv.ui.player.PlayerScreen

@Composable
fun TikTvApp(viewModel: TikTvViewModel, playing: VideoItem?) {
    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(viewModel = viewModel)
        playing?.let { video ->
            PlayerScreen(video = video, onBack = viewModel::backToHome)
        }
    }
}
