package ph.edu.batstateu.ecoconnect.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.repository.ConfirmationRepository

sealed interface OnlineConfirmState {
    data object Idle : OnlineConfirmState
    data object Asking : OnlineConfirmState
    data object Saving : OnlineConfirmState
    data object Done : OnlineConfirmState
    data class Failed(val message: String) : OnlineConfirmState
}

/** What "Confirm pickup" should do right now. */
enum class ConfirmRoute { Online, Bluetooth }

/** "Confirm pickup": online when there is internet, otherwise the Bluetooth screen. */
class OnlineConfirmViewModel(
    private val repository: ConfirmationRepository,
    private val network: NetworkMonitor,
    private val currentUserId: () -> String?,
) : ViewModel() {

    private val _state = MutableStateFlow<OnlineConfirmState>(OnlineConfirmState.Idle)
    val state: StateFlow<OnlineConfirmState> = _state.asStateFlow()

    /** Bluetooth only when there's no internet; otherwise ask before confirming online. */
    fun onConfirmClicked(): ConfirmRoute {
        if (!network.isOnline()) return ConfirmRoute.Bluetooth
        _state.value = OnlineConfirmState.Asking
        return ConfirmRoute.Online
    }

    fun confirm(requestId: String) {
        if (_state.value == OnlineConfirmState.Saving) return
        val userId = currentUserId() ?: run {
            _state.value = OnlineConfirmState.Failed("You're signed out. Sign in again and retry.")
            return
        }
        _state.value = OnlineConfirmState.Saving
        viewModelScope.launch {
            _state.value = repository.confirmOnline(requestId, userId).fold(
                onSuccess = { OnlineConfirmState.Done },
                onFailure = { OnlineConfirmState.Failed(it.message ?: "Couldn't confirm the pickup.") },
            )
        }
    }

    fun dismiss() {
        if (_state.value != OnlineConfirmState.Saving) _state.value = OnlineConfirmState.Idle
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                OnlineConfirmViewModel(app.confirmationRepository, app.networkMonitor, app.authRepository::currentUserId)
            }
        }
    }
}
