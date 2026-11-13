package ph.edu.batstateu.ecoconnect.data.scan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory

/** Below this, the answer is shown as "not sure" even if Gemini picked a category. */
const val MIN_CONFIDENCE = 0.5

/** What Gemini said about one photo, cleaned up. [category] is null when unsure. */
data class ScanResult(
    val category: WasteCategory?,
    val itemName: String,
    val confidence: Double,
    val reason: String,
    val model: String?,
    val latencyMs: Long?,
    /** Gemini's raw category, kept for testing ("UNSURE", or a valid id below [MIN_CONFIDENCE]). */
    val rawCategory: String,
) {
    val isUnsure: Boolean get() = category == null
}

sealed interface ScanOutcome {
    data class Success(val result: ScanResult, val roundTripMs: Long) : ScanOutcome
    data object Offline : ScanOutcome
    data class Error(val message: String) : ScanOutcome
}

/** JSON returned by the `classify-waste` Edge Function. */
@Serializable
data class ClassifyResponseDto(
    @SerialName("category") val category: String? = null,
    @SerialName("itemName") val itemName: String? = null,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("latencyMs") val latencyMs: Long? = null,
)

private val scanJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Pure: function JSON → [ScanResult]. Unknown categories and low confidence become "unsure".
 * Throws [IllegalArgumentException] for malformed JSON.
 */
fun parseClassifyResponse(body: String): ScanResult {
    val dto = try {
        scanJson.decodeFromString(ClassifyResponseDto.serializer(), body)
    } catch (e: Exception) {
        throw IllegalArgumentException("Unreadable scanner response", e)
    }
    val raw = dto.category?.trim()?.uppercase().orEmpty()
    val confidence = (dto.confidence ?: 0.0).coerceIn(0.0, 1.0)
    val category = WasteCategory.fromId(raw)?.takeIf { confidence >= MIN_CONFIDENCE }
    return ScanResult(
        category = category,
        itemName = dto.itemName?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown item",
        confidence = confidence,
        reason = dto.reason?.trim().orEmpty(),
        model = dto.model,
        latencyMs = dto.latencyMs,
        rawCategory = raw.ifEmpty { "UNSURE" },
    )
}

/** Pure: scales (width, height) so the longest side is at most [maxSide], keeping the aspect ratio. */
fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_IMAGE_SIDE): Pair<Int, Int> {
    require(width > 0 && height > 0) { "Empty image" }
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toDouble() / longest
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}

const val MAX_IMAGE_SIDE = 1024
