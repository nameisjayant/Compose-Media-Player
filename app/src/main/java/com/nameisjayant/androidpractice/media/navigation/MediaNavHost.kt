package com.nameisjayant.androidpractice.media.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nameisjayant.androidpractice.media.reels.presentation.ReelsScreen
import com.nameisjayant.androidpractice.media.videos.presentation.VideosScreen

/**
 * The tabs. The video player isn't a destination here: it floats over them, so it can shrink into
 * a window that keeps playing while the user moves between tabs.
 */
@Composable
fun MediaNavHost(
    navController: NavHostController,
    onVideoClick: (videoId: String) -> Unit,
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
                onVideoClick = onVideoClick,
                contentPadding = contentPadding,
            )
        }
    }
}
