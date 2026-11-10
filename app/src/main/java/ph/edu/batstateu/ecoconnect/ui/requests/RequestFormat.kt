package ph.edu.batstateu.ecoconnect.ui.requests

import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Pickup dates are UTC-midnight millis, so format in UTC. */
internal fun formatPickupDate(utcMillis: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(utcMillis)

/** Real moments (createdAt, sentAt, last sync) in the phone's time zone. */
internal fun formatMoment(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.US).format(epochMillis)

/** "7:12 PM" today, otherwise "Nov 10, 7:12 PM". */
internal fun formatLastSynced(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val day = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) } }
    val pattern = if (day(epochMillis) == day(now)) "h:mm a" else "MMM d, h:mm a"
    return SimpleDateFormat(pattern, Locale.US).format(epochMillis)
}

/** "4 s", "2 min 5 s", "1 h 3 min". */
internal fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "$h h $m min"
        m > 0 -> "$m min $s s"
        totalSeconds > 0 -> "$s s"
        else -> "${millis.coerceAtLeast(0)} ms"
    }
}

internal fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

internal fun WasteItem.categoryLabel(): String = WasteCategory.fromId(category)?.label ?: category

internal fun WasteItem.describe(): String = "${categoryLabel()} ${formatKg(kg)} kg"
