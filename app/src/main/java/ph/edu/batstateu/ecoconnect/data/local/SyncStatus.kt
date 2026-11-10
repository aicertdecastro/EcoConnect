package ph.edu.batstateu.ecoconnect.data.local

/**
 * Lifecycle of a collection request.
 * Stored in Room by [name], so renaming a value is a schema change.
 */
enum class SyncStatus {
    /** Saved on the phone, not yet sent to the server. */
    PENDING_SUBMISSION,

    /** Uploaded; waiting for a collector to respond. */
    SENT,
    ACCEPTED,
    DECLINED,

    /** Collector proposed a different date (see `proposedDate`). */
    RESCHEDULED,
    COLLECTED,
}
