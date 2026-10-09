package com.nameisjayant.composevideos.media.videos.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.ui.MediaColors

/** YouTube's speed steps. */
private val PlaybackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private val PanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val PanelMaxWidth = 480.dp

private enum class SettingsPage { Main, Quality, Speed }

/** The video heights the current video comes in, tallest first, and the one playing now. */
@Stable
class VideoQualities(val available: List<Int>, val playing: Int?)

/**
 * The quality, speed and ambient mode menu, sliding up from the bottom of the player. Picking an
 * option applies it and closes the menu; tapping outside or Back closes it too. Flipping ambient
 * mode leaves it open, like any switch.
 *
 * @param maxQuality the height the user capped the video at, or null for Auto.
 */
@Composable
fun PlaybackSettingsPanel(
    visible: Boolean,
    speed: Float,
    maxQuality: Int?,
    qualities: VideoQualities,
    ambientMode: Boolean,
    onSpeed: (Float) -> Unit,
    onQuality: (Int?) -> Unit,
    onAmbientModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    // Always opens on the main page.
    LaunchedEffect(visible) { if (visible) page = SettingsPage.Main }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close settings",
                        onClick = onDismiss,
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .widthIn(max = PanelMaxWidth)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    )
                    .clip(PanelShape)
                    .background(MediaColors.Surface)
                    // Taps between the rows stay on the panel instead of closing it.
                    .pointerInput(Unit) {}
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        // Sub-pages come in from the end; going back slides the other way.
                        val forward = targetState != SettingsPage.Main
                        slideInHorizontally { if (forward) it else -it } + fadeIn() togetherWith
                            slideOutHorizontally { if (forward) -it else it } + fadeOut()
                    },
                    label = "playbackSettings",
                ) { shown ->
                    Column(Modifier.fillMaxWidth()) {
                        when (shown) {
                            SettingsPage.Main -> {
                                MenuRow(
                                    icon = R.drawable.ic_quality,
                                    label = "Quality",
                                    value = qualityLabel(maxQuality, qualities.playing),
                                    onClick = { page = SettingsPage.Quality },
                                )
                                MenuRow(
                                    icon = R.drawable.ic_speed,
                                    label = "Playback speed",
                                    value = speedLabel(speed),
                                    onClick = { page = SettingsPage.Speed },
                                )
                                ToggleRow(
                                    icon = R.drawable.ic_ambient,
                                    label = "Ambient mode",
                                    checked = ambientMode,
                                    onCheckedChange = onAmbientModeChange,
                                )
                            }

                            SettingsPage.Quality -> {
                                PageHeader(title = "Quality", onBack = { page = SettingsPage.Main })
                                OptionRow(
                                    label = if (maxQuality == null) qualityLabel(null, qualities.playing) else "Auto",
                                    selected = maxQuality == null,
                                    onClick = { onQuality(null) },
                                )
                                qualities.available.forEach { height ->
                                    OptionRow(
                                        label = "${height}p",
                                        selected = maxQuality == height,
                                        onClick = { onQuality(height) },
                                    )
                                }
                            }

                            SettingsPage.Speed -> {
                                PageHeader(title = "Playback speed", onBack = { page = SettingsPage.Main })
                                PlaybackSpeeds.forEach { option ->
                                    OptionRow(
                                        label = speedLabel(option),
                                        selected = speed == option,
                                        onClick = { onSpeed(option) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A main-page row: icon, name, the current value and a chevron into its page. */
@Composable
private fun MenuRow(icon: Int, label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MediaColors.OnCanvas,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = MediaColors.OnCanvas,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, color = MediaColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MediaColors.Muted,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}

/** A main-page row that switches something on or off in place, rather than opening a page. */
@Composable
private fun ToggleRow(icon: Int, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MediaColors.OnCanvas,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = MediaColors.OnCanvas,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // The whole row toggles, so the switch itself just shows the value.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MediaColors.Canvas,
                checkedTrackColor = MediaColors.Accent,
                uncheckedThumbColor = MediaColors.Muted,
                uncheckedTrackColor = MediaColors.SurfaceRaised,
                uncheckedBorderColor = MediaColors.Hairline,
            ),
            modifier = Modifier.scale(0.8f),
        )
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Back to settings", onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = null,
                tint = MediaColors.OnCanvas,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = title, color = MediaColors.OnCanvas, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(color = MediaColors.Hairline)
    }
}

/** One choice on a sub-page, with a check by the one in use. */
@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Box(Modifier.size(22.dp)) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MediaColors.Accent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = if (selected) MediaColors.Accent else MediaColors.OnCanvas,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** "Auto (720p)" while Auto, naming what it picked once that's known; otherwise the chosen cap. */
private fun qualityLabel(maxQuality: Int?, playing: Int?): String = when {
    maxQuality != null -> "${maxQuality}p"
    playing != null -> "Auto (${playing}p)"
    else -> "Auto"
}

/** "Normal" for 1×, otherwise e.g. "1.5×" or "2×". */
private fun speedLabel(speed: Float): String = when {
    speed == 1f -> "Normal"
    speed % 1f == 0f -> "${speed.toInt()}×"
    else -> "$speed×"
}

/** The video tracks the player's current video offers, kept current as tracks load or switch. */
@Composable
fun rememberVideoQualities(player: Player): State<VideoQualities> {
    val state = remember(player) { mutableStateOf(player.currentTracks.toVideoQualities()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                state.value = tracks.toVideoQualities()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state
}

private fun Tracks.toVideoQualities(): VideoQualities {
    val video = groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val available = video
        .flatMap { group -> (0 until group.length).filter(group::isTrackSupported).map { group.getTrackFormat(it).height } }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
    val playing = video.firstNotNullOfOrNull { group ->
        (0 until group.length).firstOrNull(group::isTrackSelected)?.let { group.getTrackFormat(it).height }
    }
    return VideoQualities(available, playing?.takeIf { it > 0 })
}
