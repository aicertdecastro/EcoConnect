package ph.edu.batstateu.ecoconnect.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit

/** Schedules [SyncWorker]. All work needs a network connection and is unique, so it never piles up. */
class SyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Upload pending items as soon as there's a network connection. */
    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_SYNC)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Every 15 minutes (the minimum): upload anything pending and refresh statuses. */
    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_SYNC)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** On sign-out. Local data is untouched. */
    fun cancelAll() {
        workManager.cancelUniqueWork(SYNC_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    /** True while a sync is actually running (not merely waiting for a network). */
    fun observeSyncing(): Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK),
    ) { oneTime, periodic ->
        (oneTime + periodic).any { it.state == WorkInfo.State.RUNNING }
    }.distinctUntilChanged()

    companion object {
        const val SYNC_WORK = "ecoconnect-sync"
        const val PERIODIC_WORK = "ecoconnect-periodic-sync"
        const val TAG_SYNC = "ecoconnect-sync-tag"
        const val BACKOFF_SECONDS = 10L
    }
}
