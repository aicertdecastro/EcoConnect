package ph.edu.batstateu.ecoconnect.data.repository

import kotlinx.coroutines.flow.Flow
import ph.edu.batstateu.ecoconnect.bluetooth.ConfirmationStore
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import java.util.UUID

/**
 * Local side of Bluetooth pickup confirmation. Works fully offline: the receipt and the
 * COLLECTED status are written to Room together, and WorkManager uploads later.
 */
class ConfirmationRepository(
    private val requestDao: CollectionRequestDao,
    private val receiptDao: CollectionReceiptDao,
    /** Runs the block in one Room transaction (tests pass `{ it() }`). */
    private val inTransaction: suspend (suspend () -> Unit) -> Unit,
    private val requestSync: () -> Unit,
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
        inTransaction {
            // IGNORE on the unique requestId: a retry keeps the first receipt.
            receiptDao.insert(
                CollectionReceiptEntity(
                    receiptId = UUID.randomUUID().toString(),
                    requestId = requestId,
                    userId = userId,
                    collectorId = collectorId,
                    confirmedAt = confirmedAt,
                    durationMs = durationMs,
                ),
            )
            // Only after the receipt row exists.
            requestDao.markCollected(requestId, clock())
        }
        requestSync()
        return checkNotNull(receiptDao.getForRequest(requestId)) { "Receipt was not saved" }
    }
}
