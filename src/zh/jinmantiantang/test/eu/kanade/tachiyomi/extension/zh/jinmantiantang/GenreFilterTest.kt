package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GenreFilterTest {
    private val blocked = parseBlockedGenres("韓漫 韩漫 真人").toSet()

    @Test
    fun parsesWhitespaceCaseDuplicatesAndComments() {
        assertEquals(
            listOf("韓漫", "韩漫", "真人", "cos"),
            parseBlockedGenres(" 韓漫  韩漫\t真人\nCOS\u3000cos // ignored"),
        )
        assertTrue(parseBlockedGenres("// 韓漫 韩漫 真人").isEmpty())
        assertTrue(parseBlockedGenres(" \t\n\u3000 ").isEmpty())
    }

    @Test
    fun emptyBlockListDoesNotFetchDetails() = runBlocking {
        assertFalse(GenreFilter().isBlocked("/album/1", null, emptySet()) { error("Unexpected request") })
    }

    @Test
    fun visibleBlockedTagsDoNotFetchDetails() = runBlocking {
        for (genre in blocked) {
            assertTrue(GenreFilter().isBlocked("/album/1", "全彩, $genre", blocked) { error("Unexpected request") })
        }
    }

    @Test
    fun blocksJm356488WhenThe23rdTagIsMissingFromTheCard() = runBlocking {
        val details = (1..33).map { if (it == 23) "韩漫" else "tag$it" }
        val visible = details.take(19).joinToString()
        assertTrue(GenreFilter().isBlocked("/album/356488", visible, blocked) { details })
    }

    @Test
    fun preservesAllowedMangaAndExactTagMatching() = runBlocking {
        assertFalse(GenreFilter().isBlocked("/album/1", "全彩", blocked) { listOf("全彩", "真人版") })
    }

    @Test
    fun comparesLatinTagsCaseInsensitively() = runBlocking {
        assertTrue(GenreFilter().isBlocked("/album/1", null, setOf("cos")) { listOf("COS") })
    }

    @Test
    fun cachesCompleteTagsInsteadOfThePreviousDecision() = runBlocking {
        val filter = GenreFilter()
        var requests = 0
        val fetch: suspend () -> List<String> = {
            requests++
            listOf("韩漫")
        }
        assertFalse(filter.isBlocked("/album/1/", null, setOf("真人"), fetch))
        assertTrue(filter.isBlocked("/album/1", null, blocked, fetch))
        assertFalse(filter.isBlocked("/album/1", null, setOf("真人"), fetch))
        assertEquals(1, requests)
    }

    @Test
    fun cacheExpiresAfterTenMinutes() = runBlocking {
        var now = 0L
        var requests = 0
        val filter = GenreFilter { now }
        val fetch: suspend () -> List<String> = {
            requests++
            emptyList()
        }
        filter.isBlocked("/album/1", null, blocked, fetch)
        now = 10 * 60 * 1000L - 1
        filter.isBlocked("/album/1", null, blocked, fetch)
        assertEquals(1, requests)
        now++
        filter.isBlocked("/album/1", null, blocked, fetch)
        assertEquals(2, requests)
    }

    @Test
    fun cacheIsBounded() = runBlocking {
        val filter = GenreFilter()
        var requests = 0
        val fetch: suspend () -> List<String> = {
            requests++
            emptyList()
        }
        repeat(257) { filter.isBlocked("/album/$it", null, blocked, fetch) }
        filter.isBlocked("/album/0", null, blocked, fetch)
        assertEquals(258, requests)
    }

    @Test
    fun failedRequestsAreNotCachedOrSilentlyAllowed() = runBlocking {
        val filter = GenreFilter()
        try {
            filter.isBlocked("/album/1", null, blocked) { throw IOException("detail failed") }
            error("Expected IOException")
        } catch (_: IOException) {
            assertTrue(filter.isBlocked("/album/1", null, blocked) { listOf("真人") })
        }
    }

    @Test
    fun cancellationPropagates() = runBlocking {
        try {
            GenreFilter().isBlocked("/album/1", null, blocked) { throw CancellationException("cancelled") }
            error("Expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
