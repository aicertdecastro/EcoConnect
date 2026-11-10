package ph.edu.batstateu.ecoconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.repository.RequestRepository
import ph.edu.batstateu.ecoconnect.ui.pickup.RequestPickupUiState
import java.util.Locale

/** Placeholder until authentication exists. */
const val DEMO_USER_ID = "demo-user"

class RequestViewModel(
    private val repository: RequestRepository,
    private val userId: String = DEMO_USER_ID,
) : ViewModel() {

    val requests: StateFlow<List<CollectionRequestEntity>> =
        repository.observeRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> =
        repository.observePendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Saves the form to Room (status PENDING_SUBMISSION) and calls [onSaved] with the new id.
     * Ignored if the form is incomplete.
     */
    fun submit(form: RequestPickupUiState, onSaved: (requestId: String) -> Unit = {}) {
        if (!form.canSubmit) return
        val dateMillis = form.dateMillis ?: return
        val hour = form.hour ?: return
        val minute = form.minute ?: return

        val wasteItems = form.categories
            .filter { it.kg > 0.0 }
            .map { WasteItem(category = it.category.name, kg = it.kg) }

        viewModelScope.launch {
            val id = repository.submitRequest(
                userId = userId,
                wasteItems = wasteItems,
                address = form.address,
                pickupDate = dateMillis,
                pickupTime = String.format(Locale.US, "%02d:%02d", hour, minute),
                notes = null,
            )
            onSaved(id)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                RequestViewModel(app.requestRepository)
            }
        }
    }
}
