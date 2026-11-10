package ph.edu.batstateu.ecoconnect.data.guide

import kotlinx.serialization.json.Json

object WasteGuideParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): WasteGuide = json.decodeFromString(WasteGuide.serializer(), text)

    /** Returns human-readable problems with the guide's structure; empty means valid. */
    fun validate(guide: WasteGuide): List<String> {
        val errors = mutableListOf<String>()
        val questionIds = guide.questions.map { it.id }
        val categoryIds = guide.categories.map { it.id }.toSet()

        if (questionIds.size != questionIds.toSet().size) errors += "Duplicate question ids"
        if (categoryIds.size != guide.categories.size) errors += "Duplicate category ids"
        if (CategoryIds.UNSURE in categoryIds) errors += "UNSURE must not be a category"
        if (guide.question(guide.startQuestionId) == null) {
            errors += "Start question ${guide.startQuestionId} not found"
        }

        for (q in guide.questions) {
            for ((branch, outcome) in listOf("yes" to q.yes, "no" to q.no)) {
                val where = "${q.id}.$branch"
                when {
                    outcome.next != null && outcome.result != null -> errors += "$where has both next and result"
                    outcome.next == null && outcome.result == null -> errors += "$where has neither next nor result"
                    outcome.next != null && outcome.next !in questionIds -> errors += "$where -> unknown question ${outcome.next}"
                    outcome.result != null && outcome.result !in categoryIds -> errors += "$where -> unknown category ${outcome.result}"
                }
            }
        }

        // Reachability and cycles (DFS from the start question).
        val reachable = mutableSetOf<String>()
        fun visit(id: String, path: Set<String>) {
            if (id in path) {
                errors += "Cycle through $id"
                return
            }
            if (!reachable.add(id)) return
            val q = guide.question(id) ?: return
            listOfNotNull(q.yes.next, q.no.next).forEach { visit(it, path + id) }
        }
        if (guide.question(guide.startQuestionId) != null) visit(guide.startQuestionId, emptySet())
        (questionIds.toSet() - reachable).forEach { errors += "Question $it is unreachable" }

        guide.items.filter { it.category !in categoryIds }
            .forEach { errors += "Item '${it.name}' has unknown category ${it.category}" }

        return errors
    }
}
