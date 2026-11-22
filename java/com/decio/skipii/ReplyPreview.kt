package com.decio.skipii

import kotlin.math.ln
import kotlin.math.pow

/**
 * Picks the one reply worth showing under a comment before anything is opened (the Top view). Every reply gets a
 * score; the best one is shown only if it clears [MIN_SCORE], otherwise the thread stays folded behind its counter.
 *
 *  - Likes count on a curve that flattens (a reply with thousands doesn't bury everything newer).
 *  - A fresh reply gets a lift that fades over a day or so.
 *  - The channel's own replies and hearted ones are lifted, as are replies under a pinned or much-liked comment;
 *    very short, link-only, shouting or repeated-letter replies lose points.
 */
object ReplyPreview {
    const val MIN_SCORE = 3.0

    fun best(parent: Comment, replies: List<Comment>): Comment? {
        var best: Comment? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (reply in replies) {
            val s = score(parent, reply)
            if (s > bestScore) { best = reply; bestScore = s }
        }
        return best?.takeIf { bestScore >= MIN_SCORE }
    }

    fun score(parent: Comment, reply: Comment): Double {
        var s = ln(1.0 + reply.likes.coerceAtLeast(0))
        // Half the lift every 12 hours: 2.5 when new, about 1.1 at 14 hours, near 0 after a few days.
        s += 2.5 * 0.5.pow(ageHours(reply.ago) / 12.0)
        if (reply.isCreator) s += 3.0
        if (reply.hearted) s += 1.0
        if (parent.pinned || parent.likes >= 100) s += 0.5
        return s - lowSignal(reply.text)
    }

    /** How old a reply is, from YouTube's own words for it ("14 hr ago", "2 days ago"). */
    internal fun ageHours(ago: String?): Double {
        if (ago.isNullOrBlank()) return 24.0 * 365
        if (ago.contains("now", ignoreCase = true)) return 0.0
        val match = Regex("(\\d+)\\s*([A-Za-z]+)").find(ago) ?: return 24.0 * 365
        val n = match.groupValues[1].toDoubleOrNull() ?: return 24.0 * 365
        val unit = match.groupValues[2].lowercase()
        return n * when {
            unit.startsWith("sec") -> 1.0 / 3600
            unit.startsWith("min") -> 1.0 / 60
            unit.startsWith("h") -> 1.0
            unit.startsWith("d") -> 24.0
            unit.startsWith("w") -> 24.0 * 7
            unit.startsWith("mo") -> 24.0 * 30
            unit.startsWith("y") -> 24.0 * 365
            else -> 24.0 * 365
        }
    }

    /** Points lost for a reply that adds little: too short, only a link, shouting, or one letter held down. */
    private fun lowSignal(text: String): Double {
        // Without the @name it starts with.
        val body = text.trim().let { if (it.startsWith("@")) it.substringAfter(' ', "") else it }.trim()
        var penalty = 0.0
        if (body.count { it.isLetterOrDigit() } < 3) penalty += 1.5
        if (body.contains("http", ignoreCase = true) || body.contains("www.", ignoreCase = true)) penalty += 2.0
        val letters = body.filter { it.isLetter() }
        if (letters.length > 12 && letters.count { it.isUpperCase() } > letters.length * 0.6) penalty += 1.0
        if (Regex("(.)\\1{5,}").containsMatchIn(body)) penalty += 1.0
        return penalty
    }
}
