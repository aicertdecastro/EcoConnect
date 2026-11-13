package ph.edu.batstateu.ecoconnect.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.remote.NetworkMonitor
import ph.edu.batstateu.ecoconnect.data.scan.ScanOutcome
import ph.edu.batstateu.ecoconnect.data.scan.ScanRepository
import ph.edu.batstateu.ecoconnect.data.scan.ScanResult
import ph.edu.batstateu.ecoconnect.ui.scan.toJpeg

/** What the scanner is showing. The photo is kept only in memory. */
sealed interface ScanUiState {
    /** Live viewfinder, ready to capture. */
    data object Live : ScanUiState
    data class Analyzing(val photo: Bitmap) : ScanUiState
    data class Done(val photo: Bitmap, val result: ScanResult, val roundTripMs: Long) : ScanUiState
    data class Failed(val photo: Bitmap?, val message: String) : ScanUiState
}

class ScanViewModel(
    private val repository: ScanRepository,
    network: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Live)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val isOnline: StateFlow<Boolean> = network.observeOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), network.isOnline())

    private var job: Job? = null

    /** A captured or picked photo (already upright and ≤1024 px). */
    fun analyze(photo: Bitmap) {
        if (_state.value is ScanUiState.Analyzing) return
        _state.value = ScanUiState.Analyzing(photo)
        job = viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) { photo.toJpeg() }
            _state.value = when (val outcome = repository.classify(jpeg)) {
                is ScanOutcome.Success -> ScanUiState.Done(photo, outcome.result, outcome.roundTripMs)
                ScanOutcome.Offline -> ScanUiState.Failed(photo, "You're offline. The scanner needs internet.")
                is ScanOutcome.Error -> ScanUiState.Failed(photo, outcome.message)
            }
        }
    }

    fun showError(message: String) {
        _state.value = ScanUiState.Failed(null, message)
    }

    /** Back to the live viewfinder. */
    fun scanAgain() {
        job?.cancel()
        _state.value = ScanUiState.Live
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                ScanViewModel(app.scanRepository, app.networkMonitor)
            }
        }
    }
}
