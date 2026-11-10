package ph.edu.batstateu.ecoconnect.sync

import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity

/**
 * Everything [SyncWorker] needs, behind an interface so tests can use a fake.
 * Implementations must never delete local data; a failed upload leaves the row as it was.
 */
interface SyncBackend {
    /** Waits for the saved session to load; null when nobody is signed in. */
    suspend fun awaitSignedInUserId(): String?

    /** Oldest request of [userId] still PENDING_SUBMISSION, or null. */
    suspend fun oldestPendingRequest(userId: String): CollectionRequestEntity?

    /** Idempotent upload; on success marks the row SENT and returns its sentAt. */
    suspend fun uploadRequest(request: CollectionRequestEntity): Result<Long>

    /** Oldest receipt with uploaded = false, or null. */
    suspend fun oldestUnuploadedReceipt(): CollectionReceiptEntity?

    /** Idempotent upload; on success marks the receipt uploaded. */
    suspend fun uploadReceipt(receipt: CollectionReceiptEntity, uploadedBy: String): Result<Unit>

    /** Pulls server status into Room without touching unsent rows. */
    suspend fun refresh(userId: String): Result<Int>

    /** Records a fully successful sync (shown as "Last synced"). */
    fun onSyncCompleted(timeMillis: Long)
}

/** Implemented by the Application so [SyncWorker] can find its dependencies without a WorkerFactory. */
interface SyncBackendProvider {
    val syncBackend: SyncBackend
}
