package ph.edu.batstateu.ecoconnect

import android.app.Application
import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.local.EcoConnectDatabase
import ph.edu.batstateu.ecoconnect.data.remote.AndroidNetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.remote.RemoteRequestDataSource
import ph.edu.batstateu.ecoconnect.data.repository.RequestRepository

/** App-wide dependencies, created lazily (manual DI, no Hilt). */
class EcoConnectApp : Application() {

    /** Lives as long as the process; used for work that must outlive a screen (e.g. uploads). */
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
        }
    }

    val database: EcoConnectDatabase by lazy { EcoConnectDatabase.getInstance(this) }

    val networkMonitor: NetworkMonitor by lazy { AndroidNetworkMonitor(this) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            client = supabase,
            prefs = getSharedPreferences(AuthRepository.PREFS_NAME, Context.MODE_PRIVATE),
            scope = appScope,
            isConfigured = isSupabaseConfigured,
        )
    }

    val remoteRequestDataSource: RemoteRequestDataSource by lazy { RemoteRequestDataSource(supabase) }

    val requestRepository: RequestRepository by lazy {
        RequestRepository(
            requestDao = database.collectionRequestDao(),
            receiptDao = database.collectionReceiptDao(),
            remote = remoteRequestDataSource,
            network = networkMonitor,
            appScope = appScope,
        )
    }

    /** Read-only waste guide bundled in assets; parsed once and kept in memory. */
    val wasteGuideRepository: WasteGuideRepository by lazy {
        WasteGuideRepository(open = { assets.open(WasteGuideRepository.ASSET_NAME) })
    }

    override fun onCreate() {
        super.onCreate()
        // Once per signed-in user: adopt pre-auth rows and pull server status.
        appScope.launch {
            authRepository.authState
                .filterIsInstance<AuthState.SignedIn>()
                .map { it.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    runCatching { requestRepository.onSignedIn(userId) }
                }
        }
    }
}
