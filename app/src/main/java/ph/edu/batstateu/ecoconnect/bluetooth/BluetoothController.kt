package ph.edu.batstateu.ecoconnect.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.Closeable
import java.io.IOException

const val BT_LOG_TAG = "EcoBT"

enum class BtAvailability { NoAdapter, Off, On }

/** A phone in the device picker. */
data class BtDevice(val name: String, val address: String, val paired: Boolean)

/** Runtime permissions each role needs. BLUETOOTH/BLUETOOTH_ADMIN (≤ Android 11) are install-time. */
fun requiredBtPermissions(role: BtRole, sdk: Int = Build.VERSION.SDK_INT): List<String> = when {
    sdk >= Build.VERSION_CODES.S -> when (role) {
        BtRole.COLLECTOR -> listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        BtRole.USER -> listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }
    // Android 11 and below: scanning for nearby devices needs location.
    role == BtRole.USER -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    else -> emptyList()
}

/**
 * Thin wrapper over the Bluetooth Classic APIs. Every call that can block runs through
 * [blockingIo], so cancelling the coroutine closes the socket instead of hanging.
 * Callers must have checked [requiredBtPermissions] first; a [SecurityException] still
 * propagates so the UI can show the permission screen instead of crashing.
 */
@SuppressLint("MissingPermission") // checked by the screen before any call
class BluetoothController(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun availability(): BtAvailability = when {
        adapter == null -> BtAvailability.NoAdapter
        adapter.isEnabled -> BtAvailability.On
        else -> BtAvailability.Off
    }

    fun hasPermissions(role: BtRole): Boolean = requiredBtPermissions(role).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** This phone's Bluetooth name, as the resident will see it in their list. */
    fun phoneName(): String? = try {
        adapter?.name
    } catch (e: SecurityException) {
        null
    }

    fun pairedDevices(): List<BtDevice> = try {
        adapter?.bondedDevices.orEmpty()
            .map { BtDevice(it.displayName(), it.address, paired = true) }
            .sortedBy { it.name.lowercase() }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Nearby discoverable phones until the flow is cancelled (discovery is stopped then). */
    fun discover(): Flow<BtDevice> = callbackFlow {
        val adapter = adapter ?: run {
            close()
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                try {
                    trySend(BtDevice(device.displayName(), device.address, device.bondState == BluetoothDevice.BOND_BONDED))
                } catch (_: SecurityException) {
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND),
            ContextCompat.RECEIVER_EXPORTED, // sent by the system Bluetooth service
        )
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            if (!adapter.startDiscovery()) Log.w(BT_LOG_TAG, "startDiscovery returned false")
        } catch (e: SecurityException) {
            context.unregisterReceiver(receiver)
            throw e
        }
        awaitClose {
            runCatching { adapter.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    fun isDiscovering(): Boolean = try {
        adapter?.isDiscovering == true
    } catch (e: SecurityException) {
        false
    }

    /**
     * Collector: waits (no time limit) for one resident to connect. Cancelling closes the
     * server socket. The server socket is closed after one connection either way.
     */
    suspend fun acceptOne(): BluetoothSocket {
        val adapter = adapter ?: throw IOException("Bluetooth not available on this device")
        val server = adapter.listenUsingRfcommWithServiceRecord(ECO_BT_SERVICE_NAME, ECO_BT_UUID)
        return try {
            blockingIo(server) { server.accept() }
        } finally {
            server.closeQuietly()
        }
    }

    /** Resident: secure (paired) RFCOMM connection to the collector's phone. */
    suspend fun connect(address: String): BluetoothSocket {
        val adapter = adapter ?: throw IOException("Bluetooth not available on this device")
        // Discovery slows connecting down a lot; always stop it first.
        runCatching { adapter.cancelDiscovery() }
        val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(ECO_BT_UUID)
        try {
            blockingIo(socket) { socket.connect() }
        } catch (e: Throwable) {
            socket.closeQuietly()
            throw e
        }
        return socket
    }

    private fun BluetoothDevice.displayName(): String =
        try {
            name?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        } ?: address
}

/** Wraps a connected socket for the protocol; closing the channel closes the socket. */
fun BluetoothSocket.toMessageChannel(): MessageChannel =
    StreamMessageChannel(inputStream, outputStream, onClose = { (this as Closeable).closeQuietly() })
