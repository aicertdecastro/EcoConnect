package ph.edu.batstateu.ecoconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.repository.RequestRepository
import ph.edu.batstateu.ecoconnect.ui.pickup.RequestPickupUiState
import ph.edu.batstateu.ecoconnect.ui.pickup.isPickupDateAllowed
import java.util.Locale

/** Placeholder until authentication exists. */
const val DEMO_USER_ID = "demo-user"

class RequestViewModel(
    private val repository: RequestRepository,
    private val guideRepository: WasteGuideRepository,
    private val userId: String = DEMO_USER_ID,
) : ViewModel() {

    val requests: StateFlow<List<CollectionRequestEntity>> =
        repository.observeRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> =
        repository.observePendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _guide = MutableStateFlow<WasteGuide?>(null)

    /** Collection rules from the bundled waste guide; null until loaded. */
    val guide: StateFlow<WasteGuide?> = _guide.asStateFlow()

    init {
        viewModelScope.launch { _guide.value = guideRepository.guide() }
    }

    /**
     * Saves the form to Room (status PENDING_SUBMISSION) and calls [onSaved] with the new id.
     * Ignored if the form is incomplete, contains a non-requestable category,
     * or the date isn't a collection day for any selected category.
     */
    fun submit(form: RequestPickupUiState, onSaved: (requestId: String) -> Unit = {}) {
        if (!form.canSubmit) return
        val dateMillis = form.dateMillis ?: return
        val hour = form.hour ?: return
        val minute = form.minute ?: return

        viewModelScope.launch {
            val guide = guideRepository.guide()
            val selected = guide.categories.filter { it.id in form.selectedIds }
            if (selected.size != form.selectedIds.size || selected.any { !it.requestable }) return@launch
            if (!isPickupDateAllowed(dateMillis, selected)) return@launch

            val wasteItems = form.categories
                .filter { it.kg > 0.0 }
                .map { WasteItem(category = it.category.id, kg = it.kg) }

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
                RequestViewModel(app.requestRepository, app.wasteGuideRepository)
            }
        }
    }
}
