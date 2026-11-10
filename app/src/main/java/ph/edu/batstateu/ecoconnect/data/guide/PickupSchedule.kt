package ph.edu.batstateu.ecoconnect.data.guide

/** Which pickup dates are allowed for a set of selected categories. Pure Kotlin. */
object PickupSchedule {

    data class Coverage(val collected: List<GuideCategory>, val notCollected: List<GuideCategory>)

    /** A category with no published days (RESIDUAL) can be requested on any day. */
    fun collectsOn(category: GuideCategory, day: Weekday): Boolean =
        category.collectionDays.isEmpty() || day in category.collectionDays

    /** True if at least one selected (requestable) category is collected on [day]. */
    fun isDateAllowed(day: Weekday, selected: List<GuideCategory>): Boolean =
        selected.any { it.requestable && collectsOn(it, day) }

    fun coverage(day: Weekday, selected: List<GuideCategory>): Coverage {
        val (collected, notCollected) = selected.partition { collectsOn(it, day) }
        return Coverage(collected, notCollected)
    }
}
