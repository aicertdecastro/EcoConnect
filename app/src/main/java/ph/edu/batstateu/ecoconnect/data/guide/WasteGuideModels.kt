package ph.edu.batstateu.ecoconnect.data.guide

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.TimeZone

/** Root of `assets/waste_guide_alangilan.json`. Read-only reference data (not stored in Room). */
@Serializable
data class WasteGuide(
    val version: Int,
    val community: String,
    val source: String,
    val policy: String,
    val collectionMethod: String = "",
    val reviewedBy: String? = null,
    val generalNote: String,
    val startQuestionId: String,
    val unsure: UnsureAdvice,
    val categories: List<GuideCategory>,
    val questions: List<GuideQuestion>,
    val items: List<GuideItem>,
) {
    fun category(id: String): GuideCategory? = categories.firstOrNull { it.id == id }
    fun question(id: String): GuideQuestion? = questions.firstOrNull { it.id == id }
}

@Serializable
data class GuideCategory(
    /** One of [CategoryIds]. */
    val id: String,
    val name: String,
    val examples: List<String> = emptyList(),
    val instructions: List<String>,
    /** False for categories that can't be put in a door-to-door pickup request (e.g. SPECIAL). */
    val requestable: Boolean,
    /** Empty = no published day. For requestable categories this means any date may be requested. */
    val collectionDays: List<Weekday> = emptyList(),
    val scheduleNote: String? = null,
)

@Serializable
data class GuideQuestion(
    val id: String,
    val text: String,
    val hint: String? = null,
    val yes: Outcome,
    val no: Outcome,
)

/** Exactly one of [next] (a question id) or [result] (a category id) must be set. */
@Serializable
data class Outcome(
    val next: String? = null,
    val result: String? = null,
)

@Serializable
data class GuideItem(
    val name: String,
    /** A category id from [CategoryIds]. */
    val category: String,
    val keywords: List<String> = emptyList(),
    val tip: String? = null,
)

@Serializable
data class UnsureAdvice(
    val title: String,
    val instructions: List<String>,
)

object CategoryIds {
    const val BIODEGRADABLE = "BIODEGRADABLE"
    const val RECYCLABLE = "RECYCLABLE"
    const val RESIDUAL = "RESIDUAL"
    const val SPECIAL = "SPECIAL"

    /** Result id for "I'm not sure" answers; never a real category. */
    const val UNSURE = "UNSURE"
}

@Serializable
enum class Weekday(val label: String) {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday"),
    ;

    val shortLabel: String get() = label.take(3)

    companion object {
        /** Day of week for UTC-midnight millis, as produced by the Material 3 DatePicker. */
        fun fromUtcMillis(utcMillis: Long): Weekday {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> MONDAY
                Calendar.TUESDAY -> TUESDAY
                Calendar.WEDNESDAY -> WEDNESDAY
                Calendar.THURSDAY -> THURSDAY
                Calendar.FRIDAY -> FRIDAY
                Calendar.SATURDAY -> SATURDAY
                else -> SUNDAY
            }
        }
    }
}
