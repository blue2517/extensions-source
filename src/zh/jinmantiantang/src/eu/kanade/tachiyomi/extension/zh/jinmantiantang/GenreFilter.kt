package eu.kanade.tachiyomi.extension.zh.jinmantiantang

private val keywordSeparator = Regex("[\\s\\u3000]+")

internal fun parseBlockedGenres(value: String): List<String> = value
    .substringBefore("//")
    .lowercase()
    .split(keywordSeparator)
    .filter { it.isNotEmpty() }
    .distinct()

internal class GenreFilter(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class CachedGenres(
        val values: Set<String>,
        val fetchedAt: Long,
    )

    private val cache =
        object : LinkedHashMap<String, CachedGenres>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedGenres>): Boolean = size > 256
        }

    suspend fun isBlocked(
        url: String,
        visibleGenres: String?,
        blockedGenres: Set<String>,
        fetchGenres: suspend () -> List<String>,
    ): Boolean {
        if (blockedGenres.isEmpty()) return false
        if (visibleGenres.orEmpty().split(',').any { it.trim().lowercase() in blockedGenres }) return true

        val key = url.trimEnd('/')
        val cached = synchronized(cache) { cache[key] }
        val genres =
            if (cached != null && now() - cached.fetchedAt < 10 * 60 * 1000L) {
                cached.values
            } else {
                fetchGenres().map { it.trim().lowercase() }.toSet().also {
                    synchronized(cache) { cache[key] = CachedGenres(it, now()) }
                }
            }
        return genres.any { it in blockedGenres }
    }
}
