package com.nameisjayant.androidpractice.media.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nameisjayant.androidpractice.media.reels.presentation.ReelsScreen
import com.nameisjayant.androidpractice.media.videos.presentation.VideoPlayerScreen
import com.nameisjayant.androidpractice.media.videos.presentation.VideosScreen

@Composable
fun MediaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    NavHost(
        navController = navController,
        startDestination = MediaRoute.Reels,
        modifier = modifier,
    ) {
        composable<MediaRoute.Reels> { ReelsScreen(contentPadding = contentPadding) }
        composable<MediaRoute.Videos> {
            VideosScreen(
                onVideoClick = { navController.navigate(MediaRoute.VideoPlayer(it)) },
                contentPadding = contentPadding,
            )
        }
        composable<MediaRoute.VideoPlayer> {
            VideoPlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
