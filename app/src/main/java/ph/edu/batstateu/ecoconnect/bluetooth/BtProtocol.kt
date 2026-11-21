package ph.edu.batstateu.ecoconnect.bluetooth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

// Pure Kotlin: no Android types, so the whole protocol is unit-testable.

/** Fixed RFCOMM service UUID shared by every EcoConnect phone. Never change it. */
val ECO_BT_UUID: UUID = UUID.fromString("6b0c6f2e-6a51-4f3e-9d6c-0e5c0c1b2a77")
const val ECO_BT_SERVICE_NAME = "EcoConnect pickup"

/** Read timeout for every protocol step. */
const val BT_STEP_TIMEOUT_MS = 10_000L

/** Code both phones show so people can see they confirmed the same pickup. */
fun referenceCode(requestId: String): String = "EC-" + requestId.take(6).uppercase(Locale.US)

/**
 * Messages, one JSON object per line:
 * HELLO (resident → collector), CONFIRM or REJECT (collector → resident),
 * ACK (resident → collector), DONE (collector → resident).
 */
@Serializable
sealed class BtMessage {

    @Serializable
    @SerialName("HELLO")
    data class Hello(
        val requestId: String,
        val userId: String,
        val sentAt: Long,
        /**
         * Set when the resident already saved a receipt in an earlier, partly failed attempt,
         * so a collector that has no receipt yet reuses the same confirmedAt.
         */
        val previousConfirmedAt: Long? = null,
    ) : BtMessage()

    @Serializable
    @SerialName("CONFIRM")
    data class Confirm(val requestId: String, val collectorId: String, val confirmedAt: Long) : BtMessage()

    @Serializable
    @SerialName("REJECT")
    data class Reject(val reason: String) : BtMessage()

    @Serializable
    @SerialName("ACK")
    data class Ack(val requestId: String) : BtMessage()

    @Serializable
    @SerialName("DONE")
    data class Done(val requestId: String) : BtMessage()
}

/** The other phone sent something that isn't a valid message, or not the one expected. */
class BtProtocolException(message: String) : Exception(message)

/** No message within the step timeout. */
class BtTimeoutException(message: String = "No answer within ${BT_STEP_TIMEOUT_MS / 1000} seconds") : Exception(message)

object BtCodec {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true // newer app versions may add fields
        encodeDefaults = false
    }

    /** One line of JSON, without the trailing newline. */
    fun encode(message: BtMessage): String = json.encodeToString(BtMessage.serializer(), message)

    /** Throws [BtProtocolException] for blank, malformed or unknown input. */
    fun decode(line: String): BtMessage {
        if (line.isBlank()) throw BtProtocolException("Empty message")
        return try {
            json.decodeFromString(BtMessage.serializer(), line.trim())
        } catch (e: SerializationException) {
            throw BtProtocolException("Unreadable message")
        } catch (e: IllegalArgumentException) {
            throw BtProtocolException("Unreadable message")
        }
    }
}
