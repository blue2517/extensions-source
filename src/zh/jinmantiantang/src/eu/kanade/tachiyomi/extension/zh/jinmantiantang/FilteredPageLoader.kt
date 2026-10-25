package eu.kanade.tachiyomi.extension.zh.jinmantiantang

internal class FilteredPageLoader<T> {
    class Page<T>(
        val items: List<T>,
        val hasNextPage: Boolean,
    )

    private data class Key(
        val listing: String,
        val blockedGenres: Set<String>,
        val page: Int,
    )

    private val pages =
        object : LinkedHashMap<Key, Int>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Int>): Boolean = size > 256
        }

    suspend fun load(
        listing: String,
        blockedGenres: Set<String>,
        page: Int,
        fetchPage: suspend (Int) -> Page<T>,
    ): Page<T> {
        val key = Key(listing, blockedGenres, page)
        var actualPage =
            synchronized(pages) {
                if (page == 1) {
                    pages.keys.removeAll { it.listing == listing && it.blockedGenres == blockedGenres }
                }
                pages[key] ?: page
            }
        synchronized(pages) { pages[key] = actualPage }

        // Mihon stops on empty lists, even when hasNextPage is true.
        while (true) {
            val result = fetchPage(actualPage)
            actualPage++
            if (result.items.isNotEmpty() || !result.hasNextPage) {
                synchronized(pages) { pages[key.copy(page = page + 1)] = actualPage }
                return result
            }
        }
    }
}
