package io.github.peteroomen.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test fun launchesCountAndFade() {
        var usage = Suggestions.record(emptyMap(), "a", now, fromSearch = true)
        usage = Suggestions.record(usage, "b", now, fromSearch = false)
        assertEquals(0.97f, usage.getValue("a").opened, 0.001f)
        assertEquals(0.97f, usage.getValue("a").searched, 0.001f)
        assertEquals(1f, usage.getValue("b").opened, 0.001f)
        assertEquals(0f, usage.getValue("b").searched, 0.001f)
    }

    @Test fun newThenSearchedThenUsed() {
        val installed = mapOf("old" to 0L, "new" to now - day, "searched" to 0L, "used" to 0L)
        val usage = mapOf(
            "searched" to AppUsage(opened = 3f, searched = 3f),
            "used" to AppUsage(opened = 5f),
        )
        val picks = Suggestions.pick(installed, usage, favourites = emptyList(), now = now)
        assertEquals(listOf("new", "searched", "used"), picks.map { it.key })
        assertEquals(Reason.Installed(now - day), picks[0].reason)
        assertEquals(Reason.Searched, picks[1].reason)
        assertEquals(Reason.Used, picks[2].reason)
    }

    @Test fun newAppsStopBeingNewOnceUsed() {
        val installed = mapOf("new" to now - day)
        val picks = Suggestions.pick(installed, mapOf("new" to AppUsage(opened = 4f)), emptyList(), now)
        assertEquals(listOf(Suggestion("new", Reason.Used)), picks)
    }

    @Test fun favouritesFillInWithoutHistory() {
        val installed = mapOf("a" to 0L, "b" to 0L, "hidden" to 0L).minus("hidden")
        val picks = Suggestions.pick(installed, emptyMap(), favourites = listOf("a", "hidden", "b"), now = now)
        assertEquals(listOf("a", "b"), picks.map { it.key })
        assertTrue(picks.all { it.reason == Reason.Favourite })
    }

    @Test fun daysAgo() {
        assertEquals(0, Suggestions.daysAgo(now - 3_600_000, now))
        assertEquals(2, Suggestions.daysAgo(now - 2 * day - 1, now))
    }
}
