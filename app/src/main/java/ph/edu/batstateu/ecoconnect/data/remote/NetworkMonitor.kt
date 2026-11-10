package ph.edu.batstateu.ecoconnect.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface NetworkMonitor {
    fun isOnline(): Boolean
}

/** True when the active network claims internet access. Never throws. */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean = try {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        false
    }
}
