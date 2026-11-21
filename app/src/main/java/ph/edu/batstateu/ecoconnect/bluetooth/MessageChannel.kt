package ph.edu.batstateu.ecoconnect.bluetooth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Sends and receives protocol messages. Closing it ends the connection. */
interface MessageChannel : Closeable {
    suspend fun send(message: BtMessage)

    /**
     * Next message. Throws [BtTimeoutException] after [timeoutMs], [IOException] when the
     * other phone disconnects, [BtProtocolException] for garbage.
     */
    suspend fun receive(timeoutMs: Long = BT_STEP_TIMEOUT_MS): BtMessage
}

/**
 * Runs a blocking call on [io] so it can always be stopped. Socket reads ignore thread
 * interrupts, so on cancellation or after [timeoutMs] the [resource] is CLOSED, which makes
 * the blocked call fail at once. A timeout is reported as [BtTimeoutException].
 */
suspend fun <T> blockingIo(
    resource: Closeable,
    timeoutMs: Long? = null,
    io: CoroutineDispatcher = Dispatchers.IO,
    block: () -> T,
): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val timedOut = AtomicBoolean(false)
    val watchdog = launch {
        try {
            if (timeoutMs != null) {
                delay(timeoutMs)
                timedOut.set(true)
            } else {
                awaitCancellation()
            }
        } finally {
            if (!finished.get()) resource.closeQuietly()
        }
    }
    try {
        runInterruptible(io) { block() }
    } catch (e: IOException) {
        if (timedOut.get()) throw BtTimeoutException() else throw e
    } finally {
        finished.set(true)
        watchdog.cancel()
    }
}

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: Exception) {
    }
}

/**
 * Newline-delimited JSON over a pair of streams (a Bluetooth socket's, or in-memory ones in tests).
 * [onClose] closes the underlying socket.
 */
class StreamMessageChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onClose: () -> Unit = {},
) : MessageChannel {

    private val closed = AtomicBoolean(false)

    override suspend fun send(message: BtMessage) {
        val bytes = (BtCodec.encode(message) + "\n").toByteArray(Charsets.UTF_8)
        blockingIo(this, BT_STEP_TIMEOUT_MS, io) {
            output.write(bytes)
            output.flush()
        }
    }

    override suspend fun receive(timeoutMs: Long): BtMessage {
        while (true) {
            val line = blockingIo(this, timeoutMs, io) { readLine() }
                ?: throw IOException("The other phone disconnected")
            if (line.isNotBlank()) return BtCodec.decode(line)
        }
    }

    /** One UTF-8 line without "\n"; null at end of stream. Lines longer than [MAX_LINE] are rejected. */
    private fun readLine(): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            when {
                b == -1 -> return if (buffer.size() == 0) null else buffer.toString("UTF-8")
                b == '\n'.code -> return buffer.toString("UTF-8").trimEnd('\r')
                buffer.size() >= MAX_LINE -> throw IOException("Message too long")
                else -> buffer.write(b)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        input.closeQuietly()
        output.closeQuietly()
        try {
            onClose()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val MAX_LINE = 8 * 1024
    }
}
