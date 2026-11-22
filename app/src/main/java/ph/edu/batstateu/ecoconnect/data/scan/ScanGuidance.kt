package ph.edu.batstateu.ecoconnect.data.scan

import ph.edu.batstateu.ecoconnect.data.guide.GuideCategory
import ph.edu.batstateu.ecoconnect.data.guide.UnsureAdvice
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide

// Pure Kotlin: links a scan result to the bundled waste guide, so it's unit-testable.

/** What the scanner suggests doing next, taken from the waste guide. */
sealed interface ScanGuidance {
    /** Confident result: the guide's advice for [category]. */
    data class Known(val category: GuideCategory) : ScanGuidance {
        /** False for categories the guide doesn't collect door to door (e.g. SPECIAL). */
        val canRequestPickup: Boolean get() = category.requestable
    }

    /** Not confident: ask the guide's questions instead. [guess] is Gemini's low-confidence category, if any. */
    data class Unsure(val guess: GuideCategory?, val advice: UnsureAdvice) : ScanGuidance
}

fun guidanceFor(result: ScanResult, guide: WasteGuide): ScanGuidance {
    val known = result.category?.let { guide.category(it.id) }
    if (known != null) return ScanGuidance.Known(known)
    // Low confidence keeps Gemini's raw guess; "UNSURE" or unknown ids match no category.
    val guess = guide.categories.firstOrNull { it.id.equals(result.rawCategory.trim(), ignoreCase = true) }
    return ScanGuidance.Unsure(guess, guide.unsure)
}
