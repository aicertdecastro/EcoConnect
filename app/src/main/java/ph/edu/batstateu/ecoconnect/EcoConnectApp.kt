package ph.edu.batstateu.ecoconnect

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.auth.UserRole
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.EcoConnectDatabase
import ph.edu.batstateu.ecoconnect.data.remote.AndroidNetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.RemoteRequestDataSource
import ph.edu.batstateu.ecoconnect.data.repository.CollectorRepository
import ph.edu.batstateu.ecoconnect.data.scan.ScanRepository
import ph.edu.batstateu.ecoconnect.data.scan.SupabaseClassifierApi
import ph.edu.batstateu.ecoconnect.data.repository.RequestRepository
import ph.edu.batstateu.ecoconnect.sync.SyncBackend
import ph.edu.batstateu.ecoconnect.sync.SyncBackendProvider
import ph.edu.batstateu.ecoconnect.sync.SyncNotifications
import ph.edu.batstateu.ecoconnect.sync.SyncScheduler
import ph.edu.batstateu.ecoconnect.sync.SyncStatusStore

/** App-wide dependencies, created lazily (manual DI, no Hilt). */
class EcoConnectApp : Application(), SyncBackendProvider {

    /** Lives as long as the process; used for app-level observers. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isSupabaseConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    /** The single Supabase client. Keys come from local.properties via BuildConfig. */
    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://not-configured.invalid" },
            supabaseKey = BuildConfig.SUPABASE_KEY.ifBlank { "not-configured" },
        ) {
            install(Auth) // session is saved on the device and restored on launch
            install(Postgrest)
            install(Functions) // waste scanner (Gemini runs server-side in an Edge Function)
            requestTimeout = 40.seconds // AI classification can take longer than the 10 s default
        }
    }

    val database: EcoConnectDatabase by lazy { EcoConnectDatabase.getInstance(this) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            client = supabase,
            prefs = getSharedPreferences(AuthRepository.PREFS_NAME, Context.MODE_PRIVATE),
            scope = appScope,
            isConfigured = isSupabaseConfigured,
        )
    }

    val syncScheduler: SyncScheduler by lazy { SyncScheduler(this) }

    val syncStatusStore: SyncStatusStore by lazy {
        SyncStatusStore(getSharedPreferences(SyncStatusStore.PREFS_NAME, Context.MODE_PRIVATE))
    }

    val remoteRequestDataSource: RemoteRequestDataSource by lazy { RemoteRequestDataSource(supabase) }

    val networkMonitor: NetworkMonitor by lazy { AndroidNetworkMonitor(this) }

    // Waste scanner: photo → classify-waste Edge Function (Gemini) → category.
    val scanRepository: ScanRepository by lazy { ScanRepository(SupabaseClassifierApi(supabase), networkMonitor) }

    val collectorRepository: CollectorRepository by lazy {
        CollectorRepository(
            requestDao = database.collectionRequestDao(),
            remote = remoteRequestDataSource,
            network = networkMonitor,
            guide = { wasteGuideRepository.guide() },
            onRefreshed = syncStatusStore::setLastSyncedAt,
        )
    }

    val requestRepository: RequestRepository by lazy {
        RequestRepository(
            requestDao = database.collectionRequestDao(),
            receiptDao = database.collectionReceiptDao(),
            remote = remoteRequestDataSource,
            requestSync = { syncScheduler.enqueueSync() },
        )
    }

    /** Read-only waste guide bundled in assets; parsed once and kept in memory. */
    val wasteGuideRepository: WasteGuideRepository by lazy {
        WasteGuideRepository(open = { assets.open(WasteGuideRepository.ASSET_NAME) })
    }

    /** What [ph.edu.batstateu.ecoconnect.sync.SyncWorker] uses. */
    override val syncBackend: SyncBackend by lazy {
        object : SyncBackend {
            override suspend fun awaitSignedInUserId() = authRepository.awaitSignedInUserId()
            override suspend fun oldestPendingRequest(userId: String) = requestRepository.oldestPendingRequest(userId)
            override suspend fun uploadRequest(request: CollectionRequestEntity) = requestRepository.uploadRequest(request)
            override suspend fun oldestUnuploadedReceipt() = requestRepository.oldestUnuploadedReceipt()
            override suspend fun uploadReceipt(receipt: CollectionReceiptEntity, uploadedBy: String) =
                requestRepository.uploadReceipt(receipt, uploadedBy)
            // Collectors refresh their work lists; residents refresh their own requests.
            override suspend fun refresh(userId: String): Result<Int> =
                if (authRepository.currentRole() == UserRole.COLLECTOR) {
                    collectorRepository.refresh(userId)
                } else {
                    requestRepository.refreshFromServer(userId)
                }
            override fun onSyncCompleted(timeMillis: Long) = syncStatusStore.setLastSyncedAt(timeMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        SyncNotifications.createChannel(this)

        // Login → adopt pre-auth rows, sync now, refresh every 15 min. Logout → cancel all sync work.
        appScope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId ?: if (it is AuthState.SignedOut) SIGNED_OUT else null }
                .distinctUntilChanged()
                .collect { key ->
                    when (key) {
                        null -> Unit // still loading the saved session
                        SIGNED_OUT -> {
                            syncScheduler.cancelAll()
                            syncStatusStore.clear()
                        }
                        else -> {
                            runCatching { requestRepository.claimDemoRows(key) }
                            syncScheduler.enqueueSync()
                            syncScheduler.schedulePeriodicRefresh()
                        }
                    }
                }
        }

        // App comes to the foreground → try to upload anything pending.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (authRepository.currentUserId() != null) syncScheduler.enqueueSync()
            }
        })
    }

    private companion object {
        /** Marker that can't collide with a real user id. */
        const val SIGNED_OUT = "\u0000signed-out"
    }
}
