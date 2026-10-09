package com.nameisjayant.composevideos.media.videos.presentation

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.cast.MediaRouteButton
import androidx.media3.common.util.UnstableApi
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.ui.MediaColors
import com.nameisjayant.composevideos.media.videos.data.Video

/**
 * The Cast button: lists the TVs and speakers on the network, or, while casting, shows which one
 * it's on with a button to stop. Media3's own button, in white to sit on the video.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun CastButton(modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        MediaRouteButton(modifier = modifier)
    }
}

/**
 * Stands in for the picture while it's on the TV: the video's thumbnail, dimmed, under
 * "Casting to Living Room TV". The controls drive the TV as usual; [showInfo] is false while
 * they're up (or in the floating window), so the label makes way for the play button.
 */
@Composable
internal fun CastingBackdrop(video: Video?, device: String, showInfo: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        video?.let {
            Image(
                painter = painterResource(it.thumbnailRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_cast_connected),
                    contentDescription = null,
                    tint = MediaColors.Accent,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Casting to $device",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
