package ph.edu.batstateu.ecoconnect.data.remote

import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Pure Kotlin/JVM: no Android or Supabase types, so it's unit-testable.

/**
 * Status the server should store. Local PENDING_SUBMISSION means "not uploaded yet";
 * it becomes SENT on upload (the only status users may insert).
 */
fun SyncStatus.toServerStatus(): String = when (this) {
    SyncStatus.PENDING_SUBMISSION -> SyncStatus.SENT.name
    else -> name
}

/** Unknown or missing server values fall back to SENT (never PENDING_SUBMISSION: the row exists remotely). */
fun syncStatusFromServer(value: String?): SyncStatus =
    SyncStatus.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
        ?.takeIf { it != SyncStatus.PENDING_SUBMISSION }
        ?: SyncStatus.SENT

fun CollectionRequestEntity.toDto(): CollectionRequestDto = CollectionRequestDto(
    id = id,
    userId = userId,
    wasteItems = wasteItems,
    totalKg = totalKg,
    address = address,
    pickupDate = formatServerDate(pickupDate),
    pickupTime = pickupTime,
    notes = notes,
    status = syncStatus.toServerStatus(),
    collectorResponse = collectorResponse,
    collectorId = collectorId,
    proposedDate = proposedDate?.let(::formatServerDate),
    createdAt = formatServerTimestamp(createdAt),
    updatedAt = formatServerTimestamp(updatedAt),
)

/** Throws [IllegalArgumentException] if a date or timestamp is malformed. */
fun CollectionRequestDto.toEntity(): CollectionRequestEntity {
    val created = createdAt?.let(::parseServerTimestamp)
    return CollectionRequestEntity(
        id = id,
        userId = userId,
        wasteItems = wasteItems,
        address = address,
        pickupDate = parseServerDate(pickupDate),
        pickupTime = normalizeTime(pickupTime),
        notes = notes,
        syncStatus = syncStatusFromServer(status),
        collectorResponse = collectorResponse,
        proposedDate = proposedDate?.let(::parseServerDate),
        collectorId = collectorId,
        createdAt = created ?: 0L,
        updatedAt = updatedAt?.let(::parseServerTimestamp) ?: created ?: 0L,
    )
}

/** Server row (with embedded names) → local entity. Throws [IllegalArgumentException] if a date is malformed. */
fun CollectionRequestRowDto.toEntity(): CollectionRequestEntity {
    val created = createdAt?.let(::parseServerTimestamp)
    return CollectionRequestEntity(
        id = id,
        userId = userId,
        wasteItems = wasteItems,
        address = address,
        pickupDate = parseServerDate(pickupDate),
        pickupTime = normalizeTime(pickupTime),
        notes = notes,
        syncStatus = syncStatusFromServer(status),
        collectorResponse = collectorResponse,
        proposedDate = proposedDate?.let(::parseServerDate),
        collectorId = collectorId,
        collectorName = collector?.fullName?.takeIf { it.isNotBlank() },
        requesterName = requester?.fullName?.takeIf { it.isNotBlank() },
        createdAt = created ?: 0L,
        updatedAt = updatedAt?.let(::parseServerTimestamp) ?: created ?: 0L,
    )
}

fun CollectionReceiptEntity.toDto(uploadedBy: String): CollectionReceiptDto = CollectionReceiptDto(
    receiptId = receiptId,
    requestId = requestId,
    userId = userId,
    collectorId = collectorId,
    confirmedAt = formatServerTimestamp(confirmedAt),
    uploadedBy = uploadedBy,
)

// ---------- Merging server rows into Room ----------

sealed interface MergeAction {
    /** Row exists only on the server (e.g. made on another phone). */
    data class Insert(val entity: CollectionRequestEntity) : MergeAction

    /** Server has a newer status, collector reply or names for a row we already sent. */
    data class UpdateFromServer(val server: CollectionRequestEntity) : MergeAction

    data object Skip : MergeAction
}

/**
 * Decides how a server row affects the local copy. Local rows that are still
 * PENDING_SUBMISSION are never overwritten — they are uploaded first.
 */
fun mergeServerRequest(local: CollectionRequestEntity?, server: CollectionRequestEntity): MergeAction = when {
    local == null -> MergeAction.Insert(server)
    local.syncStatus == SyncStatus.PENDING_SUBMISSION -> MergeAction.Skip
    local.syncStatus == server.syncStatus &&
        local.collectorResponse == server.collectorResponse &&
        local.proposedDate == server.proposedDate &&
        local.collectorId == server.collectorId &&
        local.collectorName == server.collectorName &&
        local.requesterName == server.requesterName -> MergeAction.Skip
    else -> MergeAction.UpdateFromServer(server.copy(updatedAt = maxOf(server.updatedAt, local.updatedAt)))
}

// ---------- Date / time helpers (java.util only: minSdk 24 has no java.time) ----------

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

/** UTC-midnight millis (DatePicker value) → "yyyy-MM-dd". */
fun formatServerDate(utcMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = UTC }.format(utcMillis)

/** "yyyy-MM-dd" → UTC-midnight millis. */
fun parseServerDate(value: String): Long {
    val match = requireNotNull(Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(value.trim())) { "Bad date: $value" }
    val (y, m, d) = match.destructured
    return utcMillis(y.toInt(), m.toInt(), d.toInt())
}

/** Epoch millis → "2026-11-20T08:30:00.000Z". */
fun formatServerTimestamp(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = UTC }.format(epochMillis)

private val TIMESTAMP = Regex(
    """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?\s*(Z|[+-]\d{2}(?::?\d{2})?)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Parses Postgres/ISO timestamps such as "2026-11-20T08:30:00Z",
 * "2026-11-20T08:30:00.123456+00:00" or "2026-11-20 16:30:00+08". No offset means UTC.
 */
fun parseServerTimestamp(value: String): Long {
    val m = requireNotNull(TIMESTAMP.find(value.trim())) { "Bad timestamp: $value" }.groupValues
    val millisOfSecond = m[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toInt() ?: 0
    val base = utcMillis(m[1].toInt(), m[2].toInt(), m[3].toInt(), m[4].toInt(), m[5].toInt(), m[6].toIntOrNull() ?: 0)
    return base + millisOfSecond - offsetMillis(m[8])
}

private fun offsetMillis(offset: String): Long {
    if (offset.isEmpty() || offset.equals("Z", ignoreCase = true)) return 0
    val sign = if (offset.startsWith('-')) -1 else 1
    val digits = offset.drop(1).replace(":", "")
    val hours = digits.take(2).toInt()
    val minutes = digits.drop(2).takeIf { it.isNotEmpty() }?.toInt() ?: 0
    return sign * (hours * 60L + minutes) * 60_000L
}

private fun utcMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Long =
    Calendar.getInstance(UTC).apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
    }.timeInMillis

/** "08:00:00" or "8:00" → "08:00". Anything unexpected is returned unchanged. */
fun normalizeTime(value: String): String {
    val m = Regex("""^(\d{1,2}):(\d{2})""").find(value.trim()) ?: return value
    return "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}"
}
