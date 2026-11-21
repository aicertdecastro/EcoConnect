package ph.edu.batstateu.ecoconnect.bluetooth

import kotlinx.coroutines.CancellationException
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import java.io.IOException

/** Local data the handshake needs. Implemented by ConfirmationRepository (Room) and by test fakes. */
interface ConfirmationStore {
    suspend fun request(id: String): CollectionRequestEntity?

    /** This phone's receipt for [requestId], or null. */
    suspend fun receipt(requestId: String): CollectionReceiptEntity?

    /**
     * Saves the receipt (ignored if one already exists, so retries never duplicate) and marks
     * the request COLLECTED in one transaction, then schedules a sync. Returns the stored receipt.
     */
    suspend fun saveConfirmed(
        requestId: String,
        userId: String,
        collectorId: String,
        confirmedAt: Long,
        durationMs: Long,
    ): CollectionReceiptEntity
}

enum class BtRole { COLLECTOR, USER }

sealed interface HandshakeResult {
    /**
     * Receipt saved on this phone. [durationMs] is this attempt's socket-connected → saved time.
     * [missingDone] = the resident saved after ACK but DONE never arrived.
     */
    data class Confirmed(
        val requestId: String,
        val confirmedAt: Long,
        val durationMs: Long,
        val missingDone: Boolean = false,
    ) : HandshakeResult

    /** The other side (or this side) refused; nothing saved. */
    data class Rejected(val reason: String) : HandshakeResult

    /** Timeout, disconnect or garbled message before saving; nothing saved. Retry is safe. */
    data class Incomplete(val reason: String) : HandshakeResult
}

private val READY_FOR_PICKUP = setOf(SyncStatus.ACCEPTED, SyncStatus.RESCHEDULED)

/** Collector-side checks on a HELLO. */
object CollectorValidator {
    sealed interface Decision {
        data class Ok(val confirmedAt: Long) : Decision
        data class Reject(val reason: String) : Decision
    }

    const val NOT_FOUND = "Request not found on the collector's phone. Ask the collector to refresh."
    const val OTHER_COLLECTOR = "This request is assigned to another collector."
    const val OTHER_RESIDENT = "This request belongs to a different resident."
    const val WRONG_REQUEST = "The collector's phone is waiting for a different request."

    fun notReady(status: SyncStatus) = "Request is not ready for pickup (status ${status.name})."

    /**
     * @param expectedRequestId the request the collector opened the confirmation screen for
     * @param existingReceipt the collector's own receipt for this request, if a previous attempt saved one
     */
    fun validate(
        hello: BtMessage.Hello,
        expectedRequestId: String,
        request: CollectionRequestEntity?,
        collectorId: String,
        existingReceipt: CollectionReceiptEntity?,
        now: Long,
    ): Decision = when {
        hello.requestId != expectedRequestId -> Decision.Reject(WRONG_REQUEST)
        request == null -> Decision.Reject(NOT_FOUND)
        request.collectorId != collectorId -> Decision.Reject(OTHER_COLLECTOR)
        request.userId != hello.userId -> Decision.Reject(OTHER_RESIDENT)
        // Retry after a partial success: confirm again with the time already on record.
        existingReceipt != null -> Decision.Ok(existingReceipt.confirmedAt)
        request.syncStatus !in READY_FOR_PICKUP -> Decision.Reject(notReady(request.syncStatus))
        else -> Decision.Ok(hello.previousConfirmedAt?.takeIf { it in 1..now } ?: now)
    }
}

/** Resident-side checks on a CONFIRM. Returns a reason when it must NOT be accepted. */
object UserVerifier {
    const val WRONG_REQUEST = "The collector's phone confirmed a different request."
    const val WRONG_COLLECTOR = "This phone isn't signed in as the collector assigned to your request."

    fun problem(confirm: BtMessage.Confirm, request: CollectionRequestEntity): String? = when {
        confirm.requestId != request.id -> WRONG_REQUEST
        request.collectorId != null && confirm.collectorId != request.collectorId -> WRONG_COLLECTOR
        else -> null
    }
}

/** Logs one line per attempt with tag "EcoBT". */
fun interface AttemptLogger {
    fun log(role: BtRole, requestId: String, result: String, durationMs: Long, reason: String?)
}

/**
 * Runs one confirmation over an already-connected [MessageChannel]. Pure Kotlin.
 * Always closes the channel when done. Every read waits at most [stepTimeoutMs].
 */
class Handshake(
    private val store: ConfirmationStore,
    private val logger: AttemptLogger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val stepTimeoutMs: Long = BT_STEP_TIMEOUT_MS,
) {

    /** Collector (server): HELLO → CONFIRM/REJECT → ACK → save → DONE. */
    suspend fun runCollector(
        channel: MessageChannel,
        requestId: String,
        collectorId: String,
        connectedAt: Long,
    ): HandshakeResult = attempt(BtRole.COLLECTOR, requestId, channel, connectedAt) {
        val hello = channel.receive(stepTimeoutMs) as? BtMessage.Hello
            ?: throw BtProtocolException("Expected HELLO")
        val decision = CollectorValidator.validate(
            hello = hello,
            expectedRequestId = requestId,
            request = store.request(hello.requestId),
            collectorId = collectorId,
            existingReceipt = store.receipt(hello.requestId),
            now = clock(),
        )
        when (decision) {
            is CollectorValidator.Decision.Reject -> {
                runCatching { channel.send(BtMessage.Reject(decision.reason)) }
                HandshakeResult.Rejected(decision.reason)
            }
            is CollectorValidator.Decision.Ok -> {
                channel.send(BtMessage.Confirm(requestId, collectorId, decision.confirmedAt))
                val ack = channel.receive(stepTimeoutMs)
                if (ack !is BtMessage.Ack || ack.requestId != requestId) throw BtProtocolException("Expected ACK")
                val saved = store.saveConfirmed(requestId, hello.userId, collectorId, decision.confirmedAt, clock() - connectedAt)
                val duration = clock() - connectedAt
                // Saved already: a failed DONE only means the resident saves on its own timeout.
                try {
                    channel.send(BtMessage.Done(requestId))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                HandshakeResult.Confirmed(requestId, saved.confirmedAt, duration)
            }
        }
    }

    /** Resident (client): HELLO → CONFIRM (verify) → ACK → DONE → save. */
    suspend fun runUser(
        channel: MessageChannel,
        request: CollectionRequestEntity,
        userId: String,
        connectedAt: Long,
    ): HandshakeResult = attempt(BtRole.USER, request.id, channel, connectedAt) {
        val previous = store.receipt(request.id)
        channel.send(BtMessage.Hello(request.id, userId, clock(), previous?.confirmedAt))
        when (val reply = channel.receive(stepTimeoutMs)) {
            is BtMessage.Reject -> HandshakeResult.Rejected(reply.reason)
            is BtMessage.Confirm -> {
                UserVerifier.problem(reply, request)?.let { return@attempt HandshakeResult.Rejected(it) }
                channel.send(BtMessage.Ack(request.id))
                // From here the collector has (or will have) saved. If DONE is lost, save anyway
                // so a retry can fill whichever side is missing.
                val missingDone = try {
                    val done = channel.receive(stepTimeoutMs)
                    if (done !is BtMessage.Done || done.requestId != request.id) throw BtProtocolException("Expected DONE")
                    false
                } catch (e: BtTimeoutException) {
                    true
                } catch (e: IOException) {
                    true
                }
                val saved = store.saveConfirmed(request.id, userId, reply.collectorId, reply.confirmedAt, clock() - connectedAt)
                HandshakeResult.Confirmed(request.id, saved.confirmedAt, clock() - connectedAt, missingDone)
            }
            else -> throw BtProtocolException("Expected CONFIRM or REJECT")
        }
    }

    private suspend fun attempt(
        role: BtRole,
        requestId: String,
        channel: MessageChannel,
        connectedAt: Long,
        block: suspend () -> HandshakeResult,
    ): HandshakeResult {
        val result = try {
            block()
        } catch (e: CancellationException) {
            logger.log(role, requestId, "CANCELLED", clock() - connectedAt, null)
            throw e
        } catch (e: BtTimeoutException) {
            HandshakeResult.Incomplete("The other phone stopped answering (no reply in ${stepTimeoutMs / 1000} s).")
        } catch (e: IOException) {
            HandshakeResult.Incomplete("The connection was lost.")
        } catch (e: BtProtocolException) {
            HandshakeResult.Incomplete("The other phone sent an unexpected message. Make sure both phones have the latest EcoConnect.")
        } catch (e: Exception) {
            HandshakeResult.Incomplete("Couldn't save the receipt (${e.javaClass.simpleName}).")
        } finally {
            channel.closeQuietly()
        }
        when (result) {
            is HandshakeResult.Confirmed ->
                logger.log(role, requestId, if (result.missingDone) "CONFIRMED_NO_DONE" else "CONFIRMED", result.durationMs, null)
            is HandshakeResult.Rejected -> logger.log(role, requestId, "REJECTED", clock() - connectedAt, result.reason)
            is HandshakeResult.Incomplete -> logger.log(role, requestId, "INCOMPLETE", clock() - connectedAt, result.reason)
        }
        return result
    }
}
