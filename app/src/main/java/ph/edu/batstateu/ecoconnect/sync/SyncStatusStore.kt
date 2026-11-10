package ph.edu.batstateu.ecoconnect.sync

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Remembers when the last fully successful sync finished. */
class SyncStatusStore(private val prefs: SharedPreferences) {

    fun lastSyncedAt(): Long? = prefs.getLong(KEY_LAST_SYNCED, 0L).takeIf { it > 0L }

    fun setLastSyncedAt(timeMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNCED, timeMillis).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_LAST_SYNCED).apply()
    }

    fun observeLastSynced(): Flow<Long?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LAST_SYNCED || key == null) trySend(lastSyncedAt())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(lastSyncedAt())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    companion object {
        const val PREFS_NAME = "sync_status"
        private const val KEY_LAST_SYNCED = "last_synced_at"
    }
}
