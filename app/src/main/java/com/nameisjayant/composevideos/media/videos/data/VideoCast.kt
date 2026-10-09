package com.nameisjayant.composevideos.media.videos.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.nameisjayant.composevideos.MainActivity

/**
 * Read by the Cast framework from the manifest. Plays on Google's Default Media Receiver, which
 * needs no registration, and keeps a Cast notification (and lock-screen controls) while casting.
 */
class VideoCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notification = NotificationOptions.Builder()
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(notification).build())
            // Picks a session back up after the app is killed, and carries on from the TV when it ends.
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setRemoteToLocalEnabled(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** The bundled-video item the player is given for [video]: a raw resource, plus what the TV shows. */
@OptIn(UnstableApi::class)
internal fun Video.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(RawResourceDataSource.buildRawResourceUri(videoRes))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(channel)
            .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
            .build(),
    )
    .build()

/**
 * Turns the player's items into what the TV loads, and back when playback returns to the phone.
 *
 * A raw-resource URI means nothing to a TV, so a bundled video goes out as its URL on
 * [CastMediaServer], with its thumbnail for the TV's loading screen and the Cast notification.
 * Coming back, it's the raw resource again, so the phone doesn't stream its own files to itself.
 */
@OptIn(UnstableApi::class)
internal class VideoCastMediaItemConverter(private val server: CastMediaServer) : MediaItemConverter {
    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val video = BundledVideos.all.firstOrNull { it.id == mediaItem.mediaId }
            ?: return default.toMediaQueueItem(mediaItem)
        // Without a local network there's no URL to give; the TV's load fails and the player shows the error.
        val uri = server.videoUri(video) ?: return default.toMediaQueueItem(mediaItem)
        val castItem = mediaItem.buildUpon()
            .setUri(uri)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setMediaMetadata(
                mediaItem.mediaMetadata.buildUpon()
                    .setArtworkUri(server.thumbnailUri(video))
                    .build(),
            )
            .build()
        return default.toMediaQueueItem(castItem)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val item = default.toMediaItem(mediaQueueItem)
        val video = BundledVideos.all.firstOrNull { it.id == item.mediaId } ?: return item
        return video.toMediaItem()
    }
}
