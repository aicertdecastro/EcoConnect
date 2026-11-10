package ph.edu.batstateu.ecoconnect.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/**
 * Uploads everything pending (requests oldest first, then receipts), then refreshes
 * statuses from the server. Any network/server error → [Result.retry] with backoff;
 * local rows are never deleted. Uploads are idempotent upserts, so retries are safe.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val backend = (applicationContext as? SyncBackendProvider)?.syncBackend
            ?: return Result.failure().also { Log.e(TAG, "Application doesn't provide a SyncBackend") }

        val userId = backend.awaitSignedInUserId()
        if (userId == null) {
            Log.i(TAG, "No signed-in user; nothing to sync")
            return Result.success()
        }
        val attempt = runAttemptCount + 1

        // Requests: re-query each time so rows saved during this run are included.
        var uploads = 0
        while (uploads < MAX_ITEMS_PER_RUN) {
            val request = backend.oldestPendingRequest(userId) ?: break
            val sentAt = backend.uploadRequest(request).getOrElse {
                Log.w(TAG, "upload failed id=${request.id} attempt=$attempt: ${it.message}; will retry")
                return Result.retry()
            }
            uploads++
            Log.i(
                TAG,
                "uploaded id=${request.id} createdAt=${request.createdAt} sentAt=$sentAt " +
                    "delayMs=${sentAt - request.createdAt} attempt=$attempt",
            )
        }

        // Receipts.
        var receipts = 0
        while (receipts < MAX_ITEMS_PER_RUN) {
            val receipt = backend.oldestUnuploadedReceipt() ?: break
            backend.uploadReceipt(receipt, uploadedBy = userId).onFailure {
                Log.w(TAG, "receipt upload failed id=${receipt.receiptId} attempt=$attempt: ${it.message}; will retry")
                return Result.retry()
            }
            receipts++
            Log.i(TAG, "uploaded receipt id=${receipt.receiptId} requestId=${receipt.requestId} attempt=$attempt")
        }

        val refreshed = backend.refresh(userId).getOrElse {
            Log.w(TAG, "refresh failed attempt=$attempt: ${it.message}; will retry")
            return Result.retry()
        }

        backend.onSyncCompleted(System.currentTimeMillis())
        Log.i(TAG, "sync done: requests=$uploads receipts=$receipts refreshed=$refreshed attempt=$attempt")
        return Result.success()
    }

    /** Used for expedited work on Android 11 and below. */
    override suspend fun getForegroundInfo(): ForegroundInfo = SyncNotifications.foregroundInfo(applicationContext)

    companion object {
        const val TAG = "EcoSync"

        /** Safety cap so a misbehaving backend can't loop forever. */
        const val MAX_ITEMS_PER_RUN = 500
    }
}
