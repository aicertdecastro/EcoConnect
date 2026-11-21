package ph.edu.batstateu.ecoconnect.bluetooth

import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val RESIDENT = "resident-1"
const val COLLECTOR = "collector-1"
const val REQUEST_ID = "3f9a1c00-1111-2222-3333-444455556666"

fun testRequest(
    status: SyncStatus = SyncStatus.ACCEPTED,
    collectorId: String? = COLLECTOR,
    userId: String = RESIDENT,
) = CollectionRequestEntity(
    id = REQUEST_ID,
    userId = userId,
    wasteItems = listOf(WasteItem("RECYCLABLE", 2.0)),
    address = "Brgy. Alangilan",
    pickupDate = 1_795_219_200_000L,
    pickupTime = "07:30",
    syncStatus = status,
    collectorId = collectorId,
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

/**
 * One direction of an in-memory byte stream (no real Bluetooth).
 * Writer close → reader gets end of stream; reader close → its blocked read fails at once.
 */
class MemoryPipe {
    private val bytes = LinkedBlockingQueue<Int>()

    @Volatile private var writerClosed = false

    @Volatile private var readerClosed = false

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            while (true) {
                if (readerClosed) throw IOException("Stream closed")
                bytes.poll(5, TimeUnit.MILLISECONDS)?.let { return it }
                if (writerClosed && bytes.isEmpty()) return -1
            }
        }

        override fun close() {
            readerClosed = true
        }
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            if (writerClosed || readerClosed) throw IOException("Broken pipe")
            bytes.put(b and 0xFF)
        }

        override fun close() {
            writerClosed = true
        }
    }
}

/** Two connected channels, like the two ends of an RFCOMM socket. */
fun channelPair(): Pair<StreamMessageChannel, StreamMessageChannel> {
    val aToB = MemoryPipe()
    val bToA = MemoryPipe()
    return StreamMessageChannel(bToA.input, aToB.output) to StreamMessageChannel(aToB.input, bToA.output)
}

/** One phone's Room, in memory, with the same IGNORE-on-requestId rule. */
class FakeStore(vararg requests: CollectionRequestEntity) : ConfirmationStore {
    val requests = requests.associateBy { it.id }.toMutableMap()
    val receipts = mutableMapOf<String, CollectionReceiptEntity>()
    var saveCalls = 0
    var syncRequests = 0
    var failSave = false

    override suspend fun request(id: String) = requests[id]

    override suspend fun receipt(requestId: String) = receipts[requestId]

    override suspend fun saveConfirmed(
        requestId: String,
        userId: String,
        collectorId: String,
        confirmedAt: Long,
        durationMs: Long,
    ): CollectionReceiptEntity {
        saveCalls++
        if (failSave) throw IllegalStateException("disk full")
        val receipt = receipts.getOrPut(requestId) {
            CollectionReceiptEntity("r-$saveCalls", requestId, userId, collectorId, confirmedAt, durationMs = durationMs)
        }
        requests[requestId] = requests.getValue(requestId).copy(syncStatus = SyncStatus.COLLECTED)
        syncRequests++
        return receipt
    }
}

class RecordingLogger : AttemptLogger {
    val lines = mutableListOf<String>()

    @Synchronized
    override fun log(role: BtRole, requestId: String, result: String, durationMs: Long, reason: String?) {
        lines += "$role $result"
    }
}
