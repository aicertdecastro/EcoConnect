package ph.edu.batstateu.ecoconnect.ui.bluetooth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.bluetooth.AttemptLogger
import ph.edu.batstateu.ecoconnect.bluetooth.BT_LOG_TAG
import ph.edu.batstateu.ecoconnect.bluetooth.BluetoothController
import ph.edu.batstateu.ecoconnect.bluetooth.BtDevice
import ph.edu.batstateu.ecoconnect.bluetooth.BtRole
import ph.edu.batstateu.ecoconnect.bluetooth.Handshake
import ph.edu.batstateu.ecoconnect.bluetooth.HandshakeResult
import ph.edu.batstateu.ecoconnect.bluetooth.toMessageChannel
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.repository.ConfirmationRepository
import java.io.IOException

/** What must be fixed before Bluetooth can be used. */
enum class BtSetupNeed { NoAdapter, Permission, PermissionDenied, PermissionPermanentlyDenied, BluetoothOff }

sealed interface BtConfirmUiState {
    /** Not started (waiting for permissions / Bluetooth on). */
    data object Idle : BtConfirmUiState

    data class Setup(val need: BtSetupNeed) : BtConfirmUiState

    /** Collector: listening for the resident's phone. [visibleUntil] while discoverable. */
    data class CollectorWaiting(val phoneName: String?, val visibleUntil: Long? = null) : BtConfirmUiState

    /** Resident: choose the collector's phone. */
    data class PickDevice(
        val paired: List<BtDevice>,
        val found: List<BtDevice> = emptyList(),
        val scanning: Boolean = false,
    ) : BtConfirmUiState

    data class Connecting(val deviceName: String) : BtConfirmUiState
    data object Verifying : BtConfirmUiState
    data class Confirmed(val confirmedAt: Long, val durationMs: Long, val missingDone: Boolean = false) : BtConfirmUiState
    data class Rejected(val reason: String) : BtConfirmUiState
    data class Incomplete(val reason: String) : BtConfirmUiState

    val isFinished get() = this is Confirmed || this is Rejected || this is Incomplete
}

/**
 * One Bluetooth confirmation session for [requestId]. The collector listens, the resident
 * connects. All socket work runs in [viewModelScope]: leaving the screen cancels it and
 * closes every socket.
 */
class BtConfirmViewModel(
    val role: BtRole,
    val requestId: String,
    private val controller: BluetoothController,
    private val repository: ConfirmationRepository,
    private val authRepository: AuthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val request: StateFlow<CollectionRequestEntity?> = repository.observeRequest(requestId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<BtConfirmUiState>(BtConfirmUiState.Idle)
    val state: StateFlow<BtConfirmUiState> = _state.asStateFlow()

    private val handshake = Handshake(
        store = repository,
        logger = AttemptLogger { role, id, result, durationMs, reason ->
            Log.i(BT_LOG_TAG, "role=$role requestId=$id result=$result durationMs=$durationMs reason=${reason ?: "-"}")
        },
        clock = clock,
    )

    private var job: Job? = null
    private var scanJob: Job? = null
    private var lastDevice: BtDevice? = null

    /** Called once Bluetooth is ready; does nothing if a session is already running. */
    fun start() {
        if (_state.value != BtConfirmUiState.Idle) return
        begin()
    }

    /** Bluetooth/permission was lost before the confirmation started: go back to the setup gate. */
    fun stopIfNotFinished() {
        if (!_state.value.isFinished) reset()
    }

    fun reset() {
        cancelWork()
        _state.value = BtConfirmUiState.Idle
    }

    fun retry() {
        val device = lastDevice
        if (role == BtRole.USER && device != null) connect(device) else begin()
    }

    /** Resident: back to the device list. */
    fun chooseAnotherPhone() {
        cancelWork()
        showPicker()
    }

    fun cancel() = cancelWork()

    /** Collector: the "Make this phone visible" dialog was accepted for [seconds]. */
    fun onVisible(seconds: Int) {
        if (seconds <= 0) return
        _state.update { if (it is BtConfirmUiState.CollectorWaiting) it.copy(visibleUntil = clock() + seconds * 1000L) else it }
    }

    private fun begin() = when (role) {
        BtRole.COLLECTOR -> listen()
        BtRole.USER -> showPicker()
    }

    private fun cancelWork() {
        job?.cancel()
        scanJob?.cancel()
    }

    // ---------- Collector ----------

    private fun listen() {
        cancelWork()
        val visibleUntil = (_state.value as? BtConfirmUiState.CollectorWaiting)?.visibleUntil
        _state.value = BtConfirmUiState.CollectorWaiting(controller.phoneName(), visibleUntil)
        job = viewModelScope.launch {
            val collectorId = authRepository.currentUserId()
                ?: return@launch fail("You're signed out. Sign in again and retry.")
            val started = clock()
            val socket = try {
                controller.acceptOne()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                return@launch fail("Bluetooth permission is missing. Allow it in Settings and retry.")
            } catch (e: IOException) {
                logPreConnect("LISTEN_FAILED", started, e)
                return@launch fail("Couldn't wait for the resident's phone. Check that Bluetooth is on, then retry.")
            }
            val connectedAt = clock()
            _state.value = BtConfirmUiState.Verifying
            show(handshake.runCollector(socket.toMessageChannel(), requestId, collectorId, connectedAt))
        }
    }

    // ---------- Resident ----------

    private fun showPicker() {
        _state.value = BtConfirmUiState.PickDevice(paired = controller.pairedDevices())
    }

    fun scan() {
        if ((_state.value as? BtConfirmUiState.PickDevice)?.scanning == true) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            updatePicker { it.copy(scanning = true) }
            try {
                withTimeoutOrNull(SCAN_MILLIS) {
                    controller.discover().collect { device ->
                        updatePicker { p ->
                            if (p.paired.any { it.address == device.address } || p.found.any { it.address == device.address }) p
                            else p.copy(found = p.found + device)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.w(BT_LOG_TAG, "scan not permitted")
            } finally {
                updatePicker { it.copy(scanning = false) }
            }
        }
    }

    fun connect(device: BtDevice) {
        cancelWork()
        lastDevice = device
        _state.value = BtConfirmUiState.Connecting(device.name)
        job = viewModelScope.launch {
            val userId = authRepository.currentUserId()
                ?: return@launch fail("You're signed out. Sign in again and retry.")
            val request = repository.request(requestId)
                ?: return@launch fail("This request isn't on your phone anymore.")
            val started = clock()
            val socket = try {
                controller.connect(device.address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                return@launch fail("Bluetooth permission is missing. Allow it in Settings and retry.")
            } catch (e: IOException) {
                logPreConnect("CONNECT_FAILED", started, e)
                return@launch fail(
                    "Couldn't connect to ${device.name}. Make sure the collector's screen says " +
                        "\"Waiting for resident's phone\" and that you accepted the pairing request.",
                )
            }
            val connectedAt = clock()
            _state.value = BtConfirmUiState.Verifying
            show(handshake.runUser(socket.toMessageChannel(), request, userId, connectedAt))
        }
    }

    private fun updatePicker(change: (BtConfirmUiState.PickDevice) -> BtConfirmUiState.PickDevice) =
        _state.update { if (it is BtConfirmUiState.PickDevice) change(it) else it }

    // ---------- Results ----------

    private fun show(result: HandshakeResult) {
        _state.value = when (result) {
            is HandshakeResult.Confirmed -> BtConfirmUiState.Confirmed(result.confirmedAt, result.durationMs, result.missingDone)
            is HandshakeResult.Rejected -> BtConfirmUiState.Rejected(result.reason)
            is HandshakeResult.Incomplete -> BtConfirmUiState.Incomplete(result.reason)
        }
    }

    private fun fail(reason: String) {
        _state.value = BtConfirmUiState.Incomplete(reason)
    }

    private fun logPreConnect(result: String, started: Long, e: Exception) {
        Log.i(BT_LOG_TAG, "role=$role requestId=$requestId result=$result durationMs=${clock() - started} reason=${e.message}")
    }

    companion object {
        private const val SCAN_MILLIS = 20_000L

        fun factory(role: BtRole, requestId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                BtConfirmViewModel(role, requestId, app.bluetoothController, app.confirmationRepository, app.authRepository)
            }
        }
    }
}
