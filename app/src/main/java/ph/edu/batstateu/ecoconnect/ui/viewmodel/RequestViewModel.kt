package ph.edu.batstateu.ecoconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.repository.RequestRepository
import ph.edu.batstateu.ecoconnect.sync.SyncScheduler
import ph.edu.batstateu.ecoconnect.sync.SyncStatusStore
import ph.edu.batstateu.ecoconnect.ui.pickup.RequestPickupUiState
import ph.edu.batstateu.ecoconnect.ui.pickup.isPickupDateAllowed
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class RequestViewModel(
    private val repository: RequestRepository,
    private val guideRepository: WasteGuideRepository,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    syncStatusStore: SyncStatusStore,
) : ViewModel() {

    /** Signed-in user's id; null while signed out. */
    private val userId: Flow<String?> = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.userId }
        .distinctUntilChanged()

    val requests: StateFlow<List<CollectionRequestEntity>> = userId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeRequests(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = userId
        .flatMapLatest { id -> if (id == null) flowOf(0) else repository.observePendingCount(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _guide = MutableStateFlow<WasteGuide?>(null)

    /** Collection rules from the bundled waste guide; null until loaded. */
    val guide: StateFlow<WasteGuide?> = _guide.asStateFlow()

    /** True while WorkManager is running a sync. */
    val isSyncing: StateFlow<Boolean> = syncScheduler.observeSyncing()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** When the last fully successful sync finished; null if never. */
    val lastSyncedAt: StateFlow<Long?> = syncStatusStore.observeLastSynced()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), syncStatusStore.lastSyncedAt())

    /** Guards against a double tap on Submit saving two requests. */
    private var submitting = false

    init {
        viewModelScope.launch { _guide.value = guideRepository.guide() }
    }

    /**
     * Saves the form to Room (status PENDING_SUBMISSION) and calls [onSaved] with the new id.
     * Ignored if signed out, the form is incomplete, it contains a non-requestable category,
     * or the date isn't a collection day for any selected category.
     */
    fun submit(form: RequestPickupUiState, onSaved: (requestId: String) -> Unit = {}) {
        if (submitting || !form.canSubmit) return
        val userId = authRepository.currentUserId() ?: return
        val dateMillis = form.dateMillis ?: return
        val hour = form.hour ?: return
        val minute = form.minute ?: return

        submitting = true
        viewModelScope.launch {
            try {
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
            } finally {
                submitting = false
            }
        }
    }

    /** Manual retry from the "waiting for internet" banner. Runs as soon as there is a network. */
    fun retryNow() = syncScheduler.enqueueSync()

    fun request(id: String): Flow<CollectionRequestEntity?> = repository.observeRequest(id)

    /** This phone's Bluetooth collection receipt for [id], if any. */
    fun receipt(id: String): Flow<CollectionReceiptEntity?> = repository.observeReceipt(id)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                RequestViewModel(
                    repository = app.requestRepository,
                    guideRepository = app.wasteGuideRepository,
                    authRepository = app.authRepository,
                    syncScheduler = app.syncScheduler,
                    syncStatusStore = app.syncStatusStore,
                )
            }
        }
    }
}
