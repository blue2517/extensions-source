package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FilteredPageLoaderTest {
    @Test
    fun skipsFilteredPagesAndAdvancesWithoutRepeatingResults() = runBlocking {
        val loader = FilteredPageLoader<String>()
        val requests = mutableListOf<Int>()
        val fetch: suspend (Int) -> FilteredPageLoader.Page<String> = { page ->
            requests += page
            FilteredPageLoader.Page(if (page < 3) emptyList() else listOf("manga$page"), page < 4)
        }
        assertEquals(listOf("manga3"), loader.load("popular", setOf("韩漫"), 1, fetch).items)
        val last = loader.load("popular", setOf("韩漫"), 2, fetch)
        assertEquals(listOf("manga4"), last.items)
        assertFalse(last.hasNextPage)
        assertEquals(listOf(1, 2, 3, 4), requests)
    }

    @Test
    fun stopsAtTheActualEndOfTheListing() = runBlocking {
        val requests = mutableListOf<Int>()
        val page =
            FilteredPageLoader<String>().load("popular", setOf("韩漫"), 1) {
                requests += it
                FilteredPageLoader.Page(emptyList(), it < 2)
            }
        assertEquals(emptyList<String>(), page.items)
        assertFalse(page.hasNextPage)
        assertEquals(listOf(1, 2), requests)
    }

    @Test
    fun refreshResetsPreviouslySkippedPageOffsets() = runBlocking {
        val loader = FilteredPageLoader<String>()
        loader.load("popular", emptySet(), 1) {
            FilteredPageLoader.Page(if (it == 1) emptyList() else listOf("old"), true)
        }
        val fetch: suspend (Int) -> FilteredPageLoader.Page<String> = { FilteredPageLoader.Page(listOf("new$it"), true) }
        loader.load("popular", emptySet(), 1, fetch)
        assertEquals(listOf("new2"), loader.load("popular", emptySet(), 2, fetch).items)
    }

    @Test
    fun separatesListingsAndBlockLists() = runBlocking {
        val loader = FilteredPageLoader<String>()
        loader.load("popular", setOf("韩漫"), 1) {
            FilteredPageLoader.Page(if (it < 3) emptyList() else listOf("old"), true)
        }
        val fetch: suspend (Int) -> FilteredPageLoader.Page<String> = { FilteredPageLoader.Page(listOf("manga$it"), true) }
        assertEquals(listOf("manga2"), loader.load("latest", setOf("韩漫"), 2, fetch).items)
        assertEquals(listOf("manga2"), loader.load("popular", setOf("真人"), 2, fetch).items)
    }

    @Test
    fun retryUsesTheSameMappedPage() = runBlocking {
        val loader = FilteredPageLoader<String>()
        loader.load("popular", emptySet(), 1) {
            FilteredPageLoader.Page(if (it < 3) emptyList() else listOf("old"), true)
        }
        val requests = mutableListOf<Int>()
        val fetch: suspend (Int) -> FilteredPageLoader.Page<String> = {
            requests += it
            FilteredPageLoader.Page(listOf("manga$it"), true)
        }
        loader.load("popular", emptySet(), 2, fetch)
        loader.load("popular", emptySet(), 2, fetch)
        assertEquals(listOf(4, 4), requests)
    }
}
