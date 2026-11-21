package ph.edu.batstateu.ecoconnect.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.bluetooth.COLLECTOR
import ph.edu.batstateu.ecoconnect.bluetooth.REQUEST_ID
import ph.edu.batstateu.ecoconnect.bluetooth.RESIDENT
import ph.edu.batstateu.ecoconnect.bluetooth.testRequest
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptDao
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.repository.ConfirmationRepository

/** Unique requestId + IGNORE, like the Room table. */
private class FakeReceiptDao : CollectionReceiptDao {
    val rows = MutableStateFlow<List<CollectionReceiptEntity>>(emptyList())

    override suspend fun insert(receipt: CollectionReceiptEntity): Long {
        if (rows.value.any { it.requestId == receipt.requestId || it.receiptId == receipt.receiptId }) return -1
        rows.value += receipt
        return rows.value.size.toLong()
    }
    override suspend fun getNotUploaded() = rows.value.filter { !it.uploaded }
    override suspend fun getOldestNotUploaded() = getNotUploaded().minByOrNull { it.confirmedAt }
    override suspend fun markUploaded(receiptId: String) {
        rows.value = rows.value.map { if (it.receiptId == receiptId) it.copy(uploaded = true) else it }
    }
    override suspend fun getForRequest(requestId: String) = rows.value.firstOrNull { it.requestId == requestId }
    override fun observeForRequest(requestId: String): Flow<CollectionReceiptEntity?> =
        rows.map { list -> list.firstOrNull { it.requestId == requestId } }
}

class ConfirmationRepositoryTest {

    private val requests = FakeRequestDao()
    private val receipts = FakeReceiptDao()
    private var syncs = 0
    private var transactions = 0
    private val repo = ConfirmationRepository(
        requestDao = requests,
        receiptDao = receipts,
        inTransaction = { block ->
            transactions++
            block()
        },
        requestSync = { syncs++ },
        clock = { 9_000L },
    )

    @Test
    fun saveWritesReceiptMarksCollectedAndSchedulesSync() = runTest {
        requests.upsertAll(listOf(testRequest()))

        val saved = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, confirmedAt = 5_000L, durationMs = 2_100L)

        assertEquals(5_000L, saved.confirmedAt)
        assertEquals(2_100L, saved.durationMs)
        assertEquals(false, saved.uploaded)
        assertEquals(SyncStatus.COLLECTED, requests.getById(REQUEST_ID)?.syncStatus)
        assertEquals(1, syncs)
        assertEquals(1, transactions)
    }

    @Test
    fun retryNeverDuplicatesAndKeepsFirstReceipt() = runTest {
        requests.upsertAll(listOf(testRequest()))

        val first = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, 5_000L, 2_100L)
        val second = repo.saveConfirmed(REQUEST_ID, RESIDENT, COLLECTOR, 6_000L, 900L)

        assertEquals(1, receipts.rows.value.size)
        assertEquals(first, second)
        assertEquals(5_000L, second.confirmedAt)
        assertTrue(syncs >= 1)
    }
}
