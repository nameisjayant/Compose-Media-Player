package com.nameisjayant.androidpractice.media.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nameisjayant.androidpractice.R
import com.nameisjayant.androidpractice.media.ui.MediaColors
import com.nameisjayant.androidpractice.media.ui.MediaTheme

// Placeholder until the long-form video player is built.
@Composable
fun VideosScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MediaColors.Canvas)
            // A faint champagne glow from above gives the empty canvas some depth.
            .background(
                Brush.radialGradient(
                    colors = listOf(MediaColors.Accent.copy(alpha = 0.10f), Color.Transparent),
                    radius = 1200f,
                ),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = 10.dp),
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
        ) {
            HaloIcon()
            Spacer(Modifier.height(32.dp))
            Text(
                text = "COMING SOON",
                color = MediaColors.Accent,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Long-form, beautifully",
                color = MediaColors.OnCanvas,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Sit back with full-length stories, documentaries and talks — crafted for the big moments.",
                color = MediaColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.25f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MediaColors.Accent.copy(alpha = 0.6f), Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

/** The tab icon inside concentric hairline rings, fading outwards. */
@Composable
private fun HaloIcon() {
    Box(contentAlignment = Alignment.Center) {
        listOf(168.dp to 0.06f, 128.dp to 0.12f).forEach { (size, alpha) ->
            Box(
                Modifier
                    .size(size)
                    .border(1.dp, MediaColors.Accent.copy(alpha = alpha), CircleShape),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(MediaColors.SurfaceRaised, MediaColors.Surface)),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(MediaColors.Accent.copy(alpha = 0.55f), MediaColors.Accent.copy(alpha = 0.08f)),
                    ),
                    shape = CircleShape,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_videos),
                contentDescription = null,
                tint = MediaColors.Accent,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Preview
@Composable
private fun VideosScreenPreview() {
    MediaTheme { VideosScreen() }
}
