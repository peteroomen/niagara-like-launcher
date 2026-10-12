package io.github.peteroomen.launcher.model

/**
 * How much an app has been opened, and opened from search, lately. Counts fade a little with every
 * launch of anything, so what you use now outweighs what you used months ago.
 */
data class AppUsage(val opened: Float = 0f, val searched: Float = 0f, val lastOpened: Long = 0L)

/** Why an app is suggested on the empty search screen. */
sealed interface Reason {
    data class Installed(val at: Long) : Reason
    data object Searched : Reason
    data object Used : Reason
    data object Favourite : Reason
}

data class Suggestion(val key: String, val reason: Reason)

object Suggestions {

    private const val FADE = 0.97f
    private const val FORGET_BELOW = 0.05f
    private const val DAY = 24 * 60 * 60 * 1000L
    const val NEW_FOR = 7 * DAY

    /** Records a launch of [key], [fromSearch] if it was found by typing its name. */
    fun record(usage: Map<String, AppUsage>, key: String, now: Long, fromSearch: Boolean): Map<String, AppUsage> {
        val faded = usage.mapValues { (_, u) -> u.copy(opened = u.opened * FADE, searched = u.searched * FADE) }
            .filterValues { it.opened >= FORGET_BELOW || it.searched >= FORGET_BELOW }
        val old = faded[key] ?: AppUsage()
        return faded + (key to AppUsage(old.opened + 1f, old.searched + if (fromSearch) 1f else 0f, now))
    }

    /**
     * What to show before anything's typed: apps installed in the last week that haven't been used
     * much yet (newest first), then apps often searched for, then apps often opened. With little
     * history yet, favourites fill in. [installedAt] is each shown app's install time, in order of
     * the A–Z list; hidden apps aren't in it.
     */
    fun pick(
        installedAt: Map<String, Long>,
        usage: Map<String, AppUsage>,
        favourites: List<String>,
        now: Long,
        limit: Int = 8,
    ): List<Suggestion> {
        val picked = LinkedHashMap<String, Suggestion>()
        fun add(key: String, reason: Reason) {
            if (picked.size < limit && key !in picked && key in installedAt) picked[key] = Suggestion(key, reason)
        }
        installedAt.entries
            .filter { (key, at) -> now - at in 0..NEW_FOR && (usage[key]?.opened ?: 0f) < 2f }
            .sortedByDescending { it.value }
            .take(2)
            .forEach { add(it.key, Reason.Installed(it.value)) }
        usage.entries.filter { it.value.searched >= 1f }.sortedByDescending { it.value.searched }.take(3)
            .forEach { add(it.key, Reason.Searched) }
        usage.entries.filter { it.value.opened >= 1f }.sortedByDescending { it.value.opened }
            .forEach { add(it.key, Reason.Used) }
        if (picked.size < 4) favourites.forEach { add(it, Reason.Favourite) }
        return picked.values.toList()
    }

    /** Whole days between [at] and [now]: 0 is today (or rather, the last 24 hours). */
    fun daysAgo(at: Long, now: Long): Int = ((now - at) / DAY).toInt().coerceAtLeast(0)
}
