package app.kash.core

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnreadTimesTest {
    private val minute = 60_000L
    private val loadedAt = 1_000_000_000L
    // Long after the page settled.
    private val later = loadedAt + 10 * minute

    private fun conversation(id: String, count: Int = 1, time: Long? = null, topic: Boolean = false) =
        Conversation(id, "", ConversationKind.DIRECT, if (topic) 0 else count, time = time, topic = topic)

    private fun SeenService.of(id: String) = conversations[UnreadTimes.key(id)]

    @Test
    fun aConversationTakesTheTimeWhenItIsSeenAndAgainWhenItsCountGrows() {
        var seen = UnreadTimes.update(SeenService(), listOf(conversation("a")), later, loadedAt, watchedUntil = loadedAt - 1000)
        assertEquals(SeenUnread(1, later), seen.of("a"))
        assertEquals(later, seen.until)

        // The same count, then fewer (some read): the time stays.
        seen = UnreadTimes.update(seen, listOf(conversation("a", count = 1)), later + minute, loadedAt, loadedAt - 1000)
        assertEquals(later, seen.of("a")!!.time)
        seen = UnreadTimes.update(seen, listOf(conversation("a", count = 3)), later + 2 * minute, loadedAt, loadedAt - 1000)
        assertEquals(later + 2 * minute, seen.of("a")!!.time)
        seen = UnreadTimes.update(seen, listOf(conversation("a", count = 2)), later + 3 * minute, loadedAt, loadedAt - 1000)
        assertEquals(SeenUnread(2, later + 2 * minute), seen.of("a"))

        // Read: forgotten; unread again, it is a new message.
        seen = UnreadTimes.update(seen, emptyList(), later + 4 * minute, loadedAt, loadedAt - 1000)
        assertNull(seen.of("a"))
        seen = UnreadTimes.update(seen, listOf(conversation("a", count = 1)), later + 5 * minute, loadedAt, loadedAt - 1000)
        assertEquals(later + 5 * minute, seen.of("a")!!.time)
    }

    @Test
    fun fewerMessagesAfterKashWasClosedAreNotGivenTheirOldTime() {
        // 3 unread, seen the evening before; KaSH closed; read on the phone, then a new one came, or only 2 read: 1 at the launch.
        val evening = loadedAt - 15 * 60 * minute
        val closedAt = evening + 60 * minute
        val before = SeenService(until = closedAt, conversations = mapOf(UnreadTimes.key("a") to SeenUnread(3, evening)))
        val now = loadedAt + 5000
        val seen = UnreadTimes.update(before, listOf(conversation("a", 1)), now, loadedAt, closedAt)
        assertEquals(SeenUnread(1, now, whileClosed = true, after = evening), seen.of("a"))
        // Later, while KaSH watches, one more is read: some were read, the time stays.
        val after = UnreadTimes.update(SeenService(until = later, conversations = mapOf(UnreadTimes.key("b") to SeenUnread(3, later))), listOf(conversation("b", 2)), later + minute, loadedAt, closedAt)
        assertEquals(SeenUnread(2, later), after.of("b"))
    }

    @Test
    fun aTopicKeepsTheTimeWhenItWasFirstSeen() {
        var seen = UnreadTimes.update(SeenService(), listOf(conversation("t", topic = true)), later, loadedAt, loadedAt - 1000)
        seen = UnreadTimes.update(seen, listOf(conversation("t", topic = true)), later + minute, loadedAt, loadedAt - 1000)
        assertEquals(SeenUnread(0, later), seen.of("t"))
    }

    @Test
    fun whatIsNewAfterALaunchCameWhileKashWasClosed() {
        val closedAt = loadedAt - 3 * 60 * minute
        val before = SeenService(until = closedAt, conversations = mapOf(UnreadTimes.key("old") to SeenUnread(2, closedAt - minute), UnreadTimes.key("grown") to SeenUnread(1, closedAt - 2 * minute)))
        val now = loadedAt + 5000
        val seen = UnreadTimes.update(before, listOf(conversation("old", 2), conversation("grown", 4), conversation("new")), now, loadedAt, closedAt)
        assertEquals(SeenUnread(2, closedAt - minute), seen.of("old"))
        assertEquals(SeenUnread(4, now, whileClosed = true, after = closedAt), seen.of("grown"))
        assertEquals(SeenUnread(1, now, whileClosed = true, after = closedAt), seen.of("new"))
        assertEquals(WhileClosed(closedAt), UnreadTimes.withTimes(listOf(conversation("new")), seen).single().whileClosed)

        // Once the page settled, what is new came while KaSH watched it.
        val settled = UnreadTimes.update(seen, listOf(conversation("old", 2), conversation("grown", 4), conversation("new"), conversation("newer")), later, loadedAt, closedAt)
        assertEquals(SeenUnread(1, later), settled.of("newer"))
    }

    @Test
    fun aServiceNeverWatchedHasNoTimeBeforeItsConversations() {
        val seen = UnreadTimes.update(SeenService(), listOf(conversation("a")), loadedAt + 1000, loadedAt, watchedUntil = null)
        assertEquals(SeenUnread(1, loadedAt + 1000, whileClosed = true, after = null), seen.of("a"))
    }

    @Test
    fun aPageLoadedAgainAtOnceMissedNothing() {
        val seen = UnreadTimes.update(SeenService(until = loadedAt - 5000), listOf(conversation("a")), loadedAt + 1000, loadedAt, loadedAt - 5000)
        assertEquals(SeenUnread(1, loadedAt + 1000), seen.of("a"))
    }

    @Test
    fun whileThePageSettlesWhatItDoesNotShowIsKept() {
        val before = SeenService(until = loadedAt - 1000, conversations = mapOf(UnreadTimes.key("a") to SeenUnread(1, loadedAt - minute)))
        // A list still loading.
        val settling = UnreadTimes.update(before, emptyList(), loadedAt + 1000, loadedAt, loadedAt - 1000)
        assertEquals(SeenUnread(1, loadedAt - minute), settling.of("a"))
        val settled = UnreadTimes.update(settling, emptyList(), later, loadedAt, loadedAt - 1000)
        assertNull(settled.of("a"))
    }

    @Test
    fun theServicesTimesAreKeptAndTheirsAlone() {
        val seen = UnreadTimes.update(SeenService(), listOf(conversation("a"), conversation("timed", time = 42L)), later, loadedAt, loadedAt - 1000)
        assertNull(seen.of("timed"))
        val withTimes = UnreadTimes.withTimes(listOf(conversation("a"), conversation("timed", time = 42L), conversation("unknown")), seen)
        assertEquals(listOf(later, 42L, null), withTimes.map { it.time })
        assertTrue(withTimes.all { it.whileClosed == null })
    }

    @Test
    fun theFileKeepsTheTimesWithoutTheConversationsIds() {
        val path = Files.createTempDirectory("kash-unread-times").resolve("unread-times.json")
        val file = UnreadTimesFile(path)
        assertEquals(emptyMap(), file.load())
        val services = mapOf(ServiceId("s1") to UnreadTimes.update(SeenService(), listOf(conversation("33612345678@c.us")), later, loadedAt, null))
        file.save(services)
        assertEquals(services, file.load())
        assertFalse("33612345678" in path.readText())
        path.toFile().writeText("not json")
        assertEquals(emptyMap(), file.load())
        path.parent.toFile().deleteRecursively()
    }
}
