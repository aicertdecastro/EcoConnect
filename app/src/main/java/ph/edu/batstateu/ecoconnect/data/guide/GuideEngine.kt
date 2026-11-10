package ph.edu.batstateu.ecoconnect.data.guide

enum class Answer { YES, NO, UNSURE }

sealed interface GuideResult {
    data class Category(val category: GuideCategory) : GuideResult
    data class Unsure(val advice: UnsureAdvice) : GuideResult
}

sealed interface GuideStep {
    /** [number] is 1-based; [maxQuestions] is the longest possible path. */
    data class Ask(val question: GuideQuestion, val number: Int, val maxQuestions: Int) : GuideStep
    data class Done(val result: GuideResult) : GuideStep
}

/**
 * Walks the yes/no question tree. Pure Kotlin, no Android dependencies.
 * Any UNSURE answer ends immediately with [GuideResult.Unsure].
 */
class GuideEngine(private val guide: WasteGuide) {

    /** Ids of the questions on the current path; last = the question being asked (or last answered). */
    private val path = ArrayDeque<String>()
    private var result: GuideResult? = null

    val maxQuestions: Int = longestPath(guide.startQuestionId)

    init {
        require(guide.question(guide.startQuestionId) != null) { "Start question missing" }
        path.addLast(guide.startQuestionId)
    }

    val current: GuideStep
        get() = result?.let { GuideStep.Done(it) }
            ?: GuideStep.Ask(requireQuestion(path.last()), path.size, maxQuestions)

    val canGoBack: Boolean get() = result != null || path.size > 1

    fun answer(answer: Answer): GuideStep {
        check(result == null) { "Already finished; call back() or restart()" }
        val question = requireQuestion(path.last())
        if (answer == Answer.UNSURE) {
            result = GuideResult.Unsure(guide.unsure)
            return current
        }
        val outcome = if (answer == Answer.YES) question.yes else question.no
        when {
            outcome.next != null -> path.addLast(outcome.next)
            outcome.result != null -> result = GuideResult.Category(
                guide.category(outcome.result) ?: error("Unknown category ${outcome.result}"),
            )
            else -> error("${question.id} has an empty outcome")
        }
        return current
    }

    /** Goes back one step. Returns false if already at the first question. */
    fun back(): Boolean {
        when {
            result != null -> result = null
            path.size > 1 -> path.removeLast()
            else -> return false
        }
        return true
    }

    fun restart(): GuideStep {
        path.clear()
        path.addLast(guide.startQuestionId)
        result = null
        return current
    }

    private fun requireQuestion(id: String) = guide.question(id) ?: error("Unknown question $id")

    private fun longestPath(id: String, seen: Set<String> = emptySet()): Int {
        if (id in seen) return 0
        val q = guide.question(id) ?: return 0
        val nexts = listOfNotNull(q.yes.next, q.no.next)
        return 1 + (nexts.maxOfOrNull { longestPath(it, seen + id) } ?: 0)
    }
}
