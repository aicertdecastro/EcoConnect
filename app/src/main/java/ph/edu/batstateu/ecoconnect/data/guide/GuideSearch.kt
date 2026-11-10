package ph.edu.batstateu.ecoconnect.data.guide

import java.util.Locale

data class SearchHit(val item: GuideItem, val category: GuideCategory)

/** Case-insensitive, partial keyword search over guide items. Pure Kotlin. */
object GuideSearch {

    fun search(guide: WasteGuide, query: String, limit: Int = 20): List<SearchHit> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()

        return guide.items
            .mapNotNull { item ->
                val rank = rank(item, q) ?: return@mapNotNull null
                val category = guide.category(item.category) ?: return@mapNotNull null
                Triple(rank, item, category)
            }
            .sortedWith(compareBy({ it.first }, { it.second.name }))
            .take(limit)
            .map { (_, item, category) -> SearchHit(item, category) }
    }

    /** Lower is better; null = no match. */
    private fun rank(item: GuideItem, q: String): Int? {
        val name = item.name.lowercase(Locale.ROOT)
        val keywords = item.keywords.map { it.lowercase(Locale.ROOT) }
        val words = (listOf(name) + keywords).flatMap { it.split(NON_WORD) }.filter { it.isNotEmpty() }

        return when {
            name == q || q in keywords -> 0
            words.any { it == q || it == q + "s" || it == q + "es" } -> 1
            name.startsWith(q) -> 2
            name.split(NON_WORD).any { it.startsWith(q) } -> 3
            keywords.any { kw -> kw.startsWith(q) || kw.split(NON_WORD).any { it.startsWith(q) } } -> 4
            q in name -> 5
            keywords.any { q in it } -> 6
            // Multi-word query whose words appear separately, e.g. "candy wrapper".
            q.split(NON_WORD).filter { it.isNotEmpty() }.let { tokens ->
                tokens.size > 1 && tokens.all { t -> words.any { it.startsWith(t) } }
            } -> 7
            else -> null
        }
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
