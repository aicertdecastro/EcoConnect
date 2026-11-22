package ph.edu.batstateu.ecoconnect.data.scan

import android.util.Base64
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor

/** Sends one image to the classifier and returns the raw JSON body. */
fun interface ClassifierApi {
    /** Throws [ClassifierHttpException] for a non-2xx reply, or an IO exception. */
    suspend fun classify(imageBase64: String, mimeType: String): String
}

class ClassifierHttpException(val status: Int, message: String) : Exception(message)

/** Calls the `classify-waste` Edge Function; supabase-kt adds the signed-in user's token. */
class SupabaseClassifierApi(private val client: SupabaseClient) : ClassifierApi {
    override suspend fun classify(imageBase64: String, mimeType: String): String {
        try {
            val response = client.functions.invoke(
                function = FUNCTION_NAME,
                body = buildJsonObject {
                    put("image", imageBase64)
                    put("mimeType", mimeType)
                },
            )
            return response.bodyAsText()
        } catch (e: RestException) {
            throw ClassifierHttpException(e.statusCode, e.error)
        }
    }

    companion object {
        const val FUNCTION_NAME = "classify-waste"
    }
}

/**
 * Waste scanner: JPEG bytes → category. Needs internet; never throws
 * (except coroutine cancellation), so a failed scan can't crash the app.
 */
class ScanRepository(
    private val api: ClassifierApi,
    private val network: NetworkMonitor,
    private val encodeBase64: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun isOnline(): Boolean = network.isOnline()

    suspend fun classify(jpeg: ByteArray): ScanOutcome {
        if (!network.isOnline()) return ScanOutcome.Offline
        if (jpeg.isEmpty()) return ScanOutcome.Error("The photo was empty. Try again.")
        val started = clock()
        return try {
            val body = api.classify(encodeBase64(jpeg), "image/jpeg")
            ScanOutcome.Success(parseClassifyResponse(body), roundTripMs = clock() - started)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClassifierHttpException) {
            Log.w(TAG, "classify failed: HTTP ${e.status}")
            ScanOutcome.Error(httpMessage(e.status))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "classify: unreadable response")
            ScanOutcome.Error("The scanner's answer could not be read. Try again.")
        } catch (e: HttpRequestException) {
            Log.w(TAG, "classify: network error after ${clock() - started} ms: ${e.cause?.javaClass?.simpleName} ${e.cause?.message}")
            ScanOutcome.Error("Couldn't reach the scanner. Check your connection and try again.")
        } catch (e: Exception) {
            Log.w(TAG, "classify: ${e.javaClass.simpleName}")
            ScanOutcome.Error("Couldn't reach the scanner. Check your connection and try again.")
        }
    }

    private companion object {
        const val TAG = "WasteScanner"
    }
}

/** Friendly text for the Edge Function's status codes. */
internal fun httpMessage(status: Int): String = when (status) {
    401, 403 -> "Your session expired. Sign in again to use the scanner."
    404 -> "The scanner isn't set up on the server yet (classify-waste function not deployed)."
    413 -> "The photo is too large. Try again."
    429 -> "Too many scans right now. Wait a moment and try again."
    500 -> "The scanner isn't configured on the server (missing Gemini key)."
    504 -> "The AI service took too long. Try again."
    else -> "The scanner had a problem (error $status). Try again."
}
