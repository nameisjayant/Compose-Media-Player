package com.nameisjayant.androidpractice.media.videos.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nameisjayant.androidpractice.R
import com.nameisjayant.androidpractice.media.ui.MediaColors
import com.nameisjayant.androidpractice.media.ui.MediaTheme
import com.nameisjayant.androidpractice.media.videos.data.BundledVideos
import com.nameisjayant.androidpractice.media.videos.data.Video

private val ThumbnailShape = RoundedCornerShape(16.dp)

@Composable
fun VideosScreen(
    onVideoClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: VideosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VideosContent(
        state = state,
        onIntent = viewModel::onIntent,
        onVideoClick = onVideoClick,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
fun VideosContent(
    state: VideosState,
    onIntent: (VideosIntent) -> Unit,
    onVideoClick: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MediaColors.Canvas)
            // A faint champagne glow from above gives the canvas some depth.
            .background(
                Brush.radialGradient(
                    colors = listOf(MediaColors.Accent.copy(alpha = 0.10f), Color.Transparent),
                    radius = 1200f,
                ),
            ),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = MediaColors.Accent,
                trackColor = Color.White.copy(alpha = 0.1f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )

            state.error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
            ) {
                Text(state.error, color = MediaColors.Muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onIntent(VideosIntent.LoadVideos) }) {
                    Text("Retry", color = MediaColors.Accent)
                }
            }

            // One column on a portrait phone, more as the screen widens (landscape, tablets).
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { VideosHeader() }
                items(state.videos, key = { it.id }) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video.id) })
                }
            }
        }
    }
}

@Composable
private fun VideosHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 4.dp),
    ) {
        Text("Videos", color = MediaColors.OnCanvas, style = MaterialTheme.typography.headlineSmall)
        Box(
            Modifier
                .padding(start = 6.dp)
                .offset(y = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MediaColors.Accent),
        )
    }
}

@Composable
private fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ThumbnailShape)
            .clickable(role = Role.Button, onClickLabel = "Play ${video.title}", onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(ThumbnailShape)
                .background(MediaColors.Surface)
                .border(1.dp, MediaColors.Hairline, ThumbnailShape),
        ) {
            Image(
                painter = painterResource(video.thumbnailRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Glassy play badge, so the thumbnail reads as tappable.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MediaColors.Glass)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = MediaColors.OnCanvas,
                    modifier = Modifier
                        .offset(x = 2.dp)
                        .size(26.dp),
                )
            }
            Text(
                text = video.duration,
                color = MediaColors.OnCanvas,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = video.title,
            color = MediaColors.OnCanvas,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${video.channel} · ${video.meta}",
            color = MediaColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Preview
@Composable
private fun VideosScreenPreview() {
    MediaTheme {
        VideosContent(
            state = VideosState(isLoading = false, videos = BundledVideos.all),
            onIntent = {},
            onVideoClick = {},
        )
    }
}
