package com.nameisjayant.composevideos.media.videos.data

import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface VideosRepository {
    suspend fun getVideos(): List<Video>

    suspend fun getVideo(id: String): Video?
}

class VideosRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VideosRepository {

    // Swap these for network calls later; the ViewModels only depend on the interface.
    override suspend fun getVideos(): List<Video> = withContext(ioDispatcher) { BundledVideos.all }

    override suspend fun getVideo(id: String): Video? =
        withContext(ioDispatcher) { BundledVideos.all.firstOrNull { it.id == id } }
}

/**
 * One-minute 16:9 clips cut from Blender Foundation open movies, all licensed CC BY,
 * so the channel credit must stay visible. Sintel and Tears of Steel are letterboxed, as released.
 * Each file carries 720p, 480p and 360p video tracks over one audio track, for the quality menu.
 * - Big Buck Bunny (2008), from 1:15, © Blender Foundation | peach.blender.org
 * - Sintel (2010), from 1:30, © Blender Foundation | durian.blender.org
 * - Tears of Steel (2012), from 0:40, © Blender Foundation | mango.blender.org
 * - Elephants Dream (2006), from 0:20, © Blender Foundation / Netherlands Media Art Institute | orange.blender.org
 */
internal object BundledVideos {
    private const val BLENDER = "Blender Foundation"

    val all = listOf(
        Video(
            id = "bbb",
            title = "Big Buck Bunny",
            channel = BLENDER,
            description = "A big, good-natured rabbit just wants a peaceful morning in the meadow. " +
                "Three mischievous rodents have other plans. Blender's cheerful open movie, " +
                "made by the Peach team.",
            duration = "1:00",
            meta = "2008 · CC BY · peach.blender.org",
            videoRes = R.raw.video_bbb,
            thumbnailRes = R.drawable.thumb_video_bbb,
        ),
        Video(
            id = "sintel",
            title = "Sintel",
            channel = BLENDER,
            description = "A lone young woman crosses snow and stone in search of a baby dragon " +
                "she once cared for. A fantasy short from Blender's Durian project.",
            duration = "1:00",
            meta = "2010 · CC BY · durian.blender.org",
            videoRes = R.raw.video_sintel,
            thumbnailRes = R.drawable.thumb_video_sintel,
        ),
        Video(
            id = "tos",
            title = "Tears of Steel",
            channel = BLENDER,
            description = "Sci-fi in a future Amsterdam, mixing live-action footage with " +
                "computer-generated effects. Made by Blender's Mango team.",
            duration = "1:00",
            meta = "2012 · CC BY · mango.blender.org",
            videoRes = R.raw.video_tos,
            thumbnailRes = R.drawable.thumb_video_tos,
        ),
        Video(
            id = "ed",
            title = "Elephants Dream",
            channel = BLENDER,
            description = "Two travellers wander through a huge, surreal machine that seems to " +
                "change around them. The first open movie, by the Orange team with the " +
                "Netherlands Media Art Institute.",
            duration = "1:00",
            meta = "2006 · CC BY · orange.blender.org",
            videoRes = R.raw.video_ed,
            thumbnailRes = R.drawable.thumb_video_ed,
        ),
    )
}
