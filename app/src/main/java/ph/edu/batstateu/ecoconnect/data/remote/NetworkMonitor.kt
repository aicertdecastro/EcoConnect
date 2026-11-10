package ph.edu.batstateu.ecoconnect.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

interface NetworkMonitor {
    fun isOnline(): Boolean

    /** Emits the current state, then every change. */
    fun observeOnline(): Flow<Boolean>
}

/** Online = the default network claims internet access. Never throws. */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean = try {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        false
    }

    override fun observeOnline(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline())
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(isOnline())
        val registered = try {
            connectivity?.registerDefaultNetworkCallback(callback)
            true
        } catch (_: Exception) {
            false
        }
        awaitClose { if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }.conflate().distinctUntilChanged()
}
