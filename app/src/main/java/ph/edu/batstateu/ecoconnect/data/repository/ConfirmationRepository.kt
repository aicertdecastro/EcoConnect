package ph.edu.batstateu.ecoconnect.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import ph.edu.batstateu.ecoconnect.bluetooth.ConfirmationStore
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.remote.CollectionReceiptDto
import ph.edu.batstateu.ecoconnect.data.remote.toDto
import java.util.UUID

/** Sends a receipt straight to the server; a collector's upload also marks the request COLLECTED. */
fun interface OnlineReceiptUploader {
    suspend fun upload(receipt: CollectionReceiptDto, markCollected: Boolean): Result<Unit>
}

/** Why an online confirmation was not saved. */
class OnlineConfirmException(message: String) : Exception(message)

/**
 * Pickup confirmation. Two ways:
 * - Online ([confirmOnline]): the receipt goes to the server first and is saved locally only
 *   after the server accepted it.
 * - Bluetooth ([saveConfirmed], no internet needed): the receipt and the COLLECTED status are
 *   written to Room together, and WorkManager uploads later.
 */
class ConfirmationRepository(
    private val requestDao: CollectionRequestDao,
    private val receiptDao: CollectionReceiptDao,
    /** Runs the block in one Room transaction (tests pass `{ it() }`). */
    private val inTransaction: suspend (suspend () -> Unit) -> Unit,
    private val requestSync: () -> Unit,
    private val uploader: OnlineReceiptUploader,
    private val clock: () -> Long = System::currentTimeMillis,
) : ConfirmationStore {

    override suspend fun request(id: String): CollectionRequestEntity? = requestDao.getById(id)

    override suspend fun receipt(requestId: String): CollectionReceiptEntity? = receiptDao.getForRequest(requestId)

    fun observeRequest(id: String): Flow<CollectionRequestEntity?> = requestDao.observeById(id)

    fun observeReceipt(requestId: String): Flow<CollectionReceiptEntity?> = receiptDao.observeForRequest(requestId)

    override suspend fun saveConfirmed(
        requestId: String,
        userId: String,
        collectorId: String,
        confirmedAt: Long,
        durationMs: Long,
    ): CollectionReceiptEntity {
        saveLocally(
            CollectionReceiptEntity(
                receiptId = UUID.randomUUID().toString(),
                requestId = requestId,
                userId = userId,
                collectorId = collectorId,
                confirmedAt = confirmedAt,
                durationMs = durationMs,
            ),
        )
        requestSync()
        return checkNotNull(receiptDao.getForRequest(requestId)) { "Receipt was not saved" }
    }

    /**
     * Confirms an accepted pickup over the internet, from the resident's or the collector's phone
     * ([myUserId] must be one of them). Nothing is written locally unless the server accepted it.
     * Never throws (except cancellation); a failure means "use Bluetooth instead".
     */
    suspend fun confirmOnline(requestId: String, myUserId: String): Result<CollectionReceiptEntity> {
        return try {
            val request = requestDao.getById(requestId)
                ?: return failure("This request isn't on your phone anymore.")
            val collectorId = request.collectorId
            if (collectorId == null || request.syncStatus !in CONFIRMABLE) {
                return failure("Only accepted pickups can be confirmed.")
            }
            val isCollector = myUserId == collectorId
            if (!isCollector && myUserId != request.userId) return failure("This pickup isn't yours.")
            receiptDao.getForRequest(requestId)?.let { return Result.success(it) } // already confirmed

            val receipt = CollectionReceiptEntity(
                receiptId = UUID.randomUUID().toString(),
                requestId = requestId,
                userId = request.userId,
                collectorId = collectorId,
                confirmedAt = clock(),
                uploaded = true,
                durationMs = null, // null = confirmed online
            )
            uploader.upload(receipt.toDto(uploadedBy = myUserId), markCollected = isCollector)
                .onFailure { return failure("Couldn't reach the server. Confirm with Bluetooth instead.") }
            saveLocally(receipt)
            Result.success(checkNotNull(receiptDao.getForRequest(requestId)) { "Receipt was not saved" })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure("Couldn't save the confirmation. Try again.")
        }
    }

    /** Receipt first, then COLLECTED, in one transaction: never COLLECTED without a receipt. */
    private suspend fun saveLocally(receipt: CollectionReceiptEntity) {
        inTransaction {
            // IGNORE on the unique requestId: a retry keeps the first receipt.
            receiptDao.insert(receipt)
            requestDao.markCollected(receipt.requestId, clock())
        }
    }

    private fun <T> failure(message: String): Result<T> = Result.failure(OnlineConfirmException(message))

    private companion object {
        val CONFIRMABLE = setOf(SyncStatus.ACCEPTED, SyncStatus.RESCHEDULED)
    }
}
