package com.nameisjayant.composevideos.media.videos.data

/** A titled part of a video starting [startMs] in; it runs until the next one starts, or the end. */
data class Chapter(val startMs: Long, val title: String)

/** A time written in a description, e.g. "1:05" or "1:02:03", and the characters it covers there. */
data class Timestamp(val range: IntRange, val positionMs: Long)

/** YouTube's rule: anything shorter isn't a chapter, so the whole list is ignored. */
private const val MIN_CHAPTER_MS = 10_000L
private const val MIN_CHAPTERS = 3

private val TimestampPattern = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):([0-5]\d)(?![\d:])""")

/** Characters allowed between a chapter's time and its title, e.g. "0:15 - Intro". */
private const val TITLE_SEPARATORS = "-–—:|·"

/** Every time written in [text], in order. Minutes over 59 only count when there are no hours. */
fun findTimestamps(text: String): List<Timestamp> = TimestampPattern.findAll(text).mapNotNull { match ->
    val (hours, minutes, seconds) = match.destructured
    val h = hours.toLongOrNull() ?: 0
    val m = minutes.toLong()
    if (hours.isNotEmpty() && m > 59) return@mapNotNull null
    Timestamp(match.range, ((h * 60 + m) * 60 + seconds.toLong()) * 1_000)
}.toList()

/**
 * Chapters from the lines of [description] that start with a time, the way YouTube reads them:
 * the first must be 0:00, there must be at least three, in order, each at least 10 seconds long.
 * Anything else gives no chapters at all rather than a half-right list.
 */
fun parseChapters(description: String): List<Chapter> {
    val chapters = description.lineSequence().mapNotNull { line ->
        val trimmed = line.trim()
        val timestamp = findTimestamps(trimmed).firstOrNull()?.takeIf { it.range.first == 0 } ?: return@mapNotNull null
        val title = trimmed.substring(timestamp.range.last + 1).trimStart { it.isWhitespace() || it in TITLE_SEPARATORS }.trim()
        Chapter(timestamp.positionMs, title).takeIf { title.isNotEmpty() }
    }.toList()
    val valid = chapters.size >= MIN_CHAPTERS &&
        chapters.first().startMs == 0L &&
        chapters.zipWithNext().all { (a, b) -> b.startMs - a.startMs >= MIN_CHAPTER_MS }
    return if (valid) chapters else emptyList()
}

/** The chapter playing at [positionMs], or null when there are none. */
fun List<Chapter>.chapterAt(positionMs: Long): Chapter? = lastOrNull { it.startMs <= positionMs } ?: firstOrNull()
