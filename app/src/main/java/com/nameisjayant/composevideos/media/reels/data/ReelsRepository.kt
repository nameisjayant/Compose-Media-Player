package com.nameisjayant.composevideos.media.reels.data

import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface ReelsRepository {
    suspend fun getReels(): List<Reel>
}

class ReelsRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ReelsRepository {

    // Swap this for a network call later; the ViewModel only depends on the interface.
    override suspend fun getReels(): List<Reel> = withContext(ioDispatcher) { BundledReels.all }
}

/**
 * 12-second vertical (720x1280) clips cut from Blender Foundation open movies, all licensed
 * CC BY (creativecommons.org/licenses/by/3.0), so the channel credit must stay visible.
 * Each title is the clip's start time in the film:
 * - Sintel (2010), © Blender Foundation | durian.blender.org
 * - Tears of Steel (2012), © Blender Foundation | mango.blender.org
 * - Elephants Dream (2006), © Blender Foundation / Netherlands Media Art Institute | orange.blender.org
 */
internal object BundledReels {
    private const val BLENDER = "Blender Foundation"

    private val reels = listOf(
        Reel("sintel_1", "Sintel at 2:10", BLENDER, R.raw.reel_sintel_1),
        Reel("tos_1", "Tears of Steel at 1:00", BLENDER, R.raw.reel_tos_1),
        Reel("ed_1", "Elephants Dream at 0:40", BLENDER, R.raw.reel_ed_1),
        Reel("sintel_2", "Sintel at 5:30", BLENDER, R.raw.reel_sintel_2),
        Reel("tos_2", "Tears of Steel at 3:35", BLENDER, R.raw.reel_tos_2),
        Reel("ed_2", "Elephants Dream at 3:00", BLENDER, R.raw.reel_ed_2),
        Reel("sintel_3", "Sintel at 8:15", BLENDER, R.raw.reel_sintel_3),
        Reel("tos_3", "Tears of Steel at 7:40", BLENDER, R.raw.reel_tos_3),
        Reel("ed_3", "Elephants Dream at 6:00", BLENDER, R.raw.reel_ed_3),
        Reel("sintel_4", "Sintel at 10:40", BLENDER, R.raw.reel_sintel_4),
        Reel("tos_4", "Tears of Steel at 9:20", BLENDER, R.raw.reel_tos_4),
        Reel("ed_4", "Elephants Dream at 8:30", BLENDER, R.raw.reel_ed_4),
    )

    // Sample engagement so the feed doesn't look empty; stable per reel so counts don't jump.
    private val sampleComments = listOf(
        "anya.frames" to "The colour grading here is unreal",
        "dev_tomas" to "Rendered in Blender?? No way",
        "lumen.studio" to "Watched this 5 times already",
        "kiri" to "Need the full film now",
        "orbit.cuts" to "That transition at the end 🔥",
        "maya.sketch" to "Open movies deserve way more love",
    )
    private val ages = listOf("2h", "5h", "1d", "3d", "1w")

    val all = reels.mapIndexed { i, reel ->
        reel.copy(
            likeCount = 1_200 + (i * 7_919) % 48_000,
            shareCount = 40 + (i * 613) % 2_400,
            comments = List(2 + i % 4) { c ->
                val (author, text) = sampleComments[(i + c) % sampleComments.size]
                ReelComment("${reel.id}_c$c", author, text, ages[(i + c) % ages.size])
            },
        )
    }
}
