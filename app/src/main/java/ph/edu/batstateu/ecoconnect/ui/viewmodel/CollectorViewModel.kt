package ph.edu.batstateu.ecoconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.auth.UserRole
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.repository.CollectorAction
import ph.edu.batstateu.ecoconnect.data.repository.CollectorRepository
import ph.edu.batstateu.ecoconnect.data.repository.RespondResult
import ph.edu.batstateu.ecoconnect.sync.SyncStatusStore
import java.util.Calendar
import java.util.TimeZone

/** "My pickups" split by pickup date relative to today. */
data class PickupGroups(
    val overdue: List<CollectionRequestEntity> = emptyList(),
    val today: List<CollectionRequestEntity> = emptyList(),
    val upcoming: List<CollectionRequestEntity> = emptyList(),
) {
    val isEmpty get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty()
}

data class CollectorHomeUiState(
    val newRequests: List<CollectionRequestEntity> = emptyList(),
    val myPickups: PickupGroups = PickupGroups(),
    val history: List<CollectionRequestEntity> = emptyList(),
    val isOnline: Boolean = true,
    val lastUpdatedAt: Long? = null,
    val isRefreshing: Boolean = false,
) {
    val newCount get() = newRequests.size
    val todayCount get() = myPickups.today.size
}

/** Pickup dates are UTC-midnight millis; "today" is the phone's local date expressed the same way. */
fun todayUtcMidnight(now: Long = System.currentTimeMillis()): Long {
    val local = Calendar.getInstance().apply { timeInMillis = now }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun groupPickups(requests: List<CollectionRequestEntity>, today: Long = todayUtcMidnight()): PickupGroups {
    // Rescheduled pickups happen on the proposed date.
    val date = { r: CollectionRequestEntity -> r.proposedDate ?: r.pickupDate }
    val sorted = requests.sortedBy(date)
    return PickupGroups(
        overdue = sorted.filter { date(it) < today },
        today = sorted.filter { date(it) == today },
        upcoming = sorted.filter { date(it) > today },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorViewModel(
    private val repository: CollectorRepository,
    private val authRepository: AuthRepository,
    private val guideRepository: WasteGuideRepository,
    syncStatusStore: SyncStatusStore,
) : ViewModel() {

    /** Only a signed-in COLLECTOR gets data; anyone else sees empty lists and can't act. */
    private val collectorId: StateFlow<String?> = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.takeIf { s -> s.role == UserRole.COLLECTOR }?.userId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val isRefreshing = MutableStateFlow(false)
    private val _responding = MutableStateFlow(false)
    val responding: StateFlow<Boolean> = _responding.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot snackbar messages. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _guide = MutableStateFlow<WasteGuide?>(null)
    val guide: StateFlow<WasteGuide?> = _guide.asStateFlow()

    val isOnline: StateFlow<Boolean> = repository.observeOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.isOnline())

    private fun <T> perCollector(empty: T, block: (String) -> Flow<T>): Flow<T> =
        collectorId.flatMapLatest { id -> if (id == null) flowOf(empty) else block(id) }

    val homeState: StateFlow<CollectorHomeUiState> = combine(
        perCollector(emptyList()) { repository.observeNew(it) },
        perCollector(emptyList()) { repository.observeMine(it) },
        perCollector(emptyList()) { repository.observeHistory(it) },
        combine(isOnline, syncStatusStore.observeLastSynced(), isRefreshing) { a, b, c -> Triple(a, b, c) },
    ) { new, mine, history, (online, lastUpdated, refreshing) ->
        CollectorHomeUiState(
            newRequests = new,
            myPickups = groupPickups(mine),
            history = history.sortedByDescending { it.updatedAt },
            isOnline = online,
            lastUpdatedAt = lastUpdated,
            isRefreshing = refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectorHomeUiState())

    init {
        viewModelScope.launch { _guide.value = guideRepository.guide() }
    }

    fun request(id: String): Flow<CollectionRequestEntity?> = repository.observeRequest(id)

    /** Pull-to-refresh and on open. Offline → keeps showing saved data. */
    fun refresh(showErrors: Boolean = true) {
        val id = collectorId.value ?: return
        if (isRefreshing.value) return
        if (!repository.isOnline()) {
            if (showErrors) _messages.trySend("Offline — showing saved data")
            return
        }
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh(id).onFailure {
                    if (showErrors) _messages.trySend("Couldn't refresh: ${AuthRepository.friendlyMessage(it)}")
                }
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun respond(request: CollectionRequestEntity, action: CollectorAction, onSuccess: () -> Unit = {}) {
        val id = collectorId.value ?: return
        if (_responding.value) return
        _responding.value = true
        viewModelScope.launch {
            try {
                val message = when (val result = repository.respond(request, id, action)) {
                    is RespondResult.Success -> {
                        onSuccess()
                        when (action) {
                            is CollectorAction.Accept -> "Request accepted"
                            is CollectorAction.Decline -> "Request declined"
                            is CollectorAction.ProposeDate -> "New date proposed"
                        }
                    }
                    RespondResult.Offline -> "You're offline. Connect to the internet to respond."
                    RespondResult.AlreadyTaken -> "Already taken by another collector."
                    is RespondResult.Invalid -> result.message
                    is RespondResult.Error -> "Couldn't send: ${result.message}"
                }
                _messages.trySend(message)
            } finally {
                _responding.value = false
            }
        }
    }

    fun showMessage(text: String) {
        _messages.trySend(text)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                CollectorViewModel(app.collectorRepository, app.authRepository, app.wasteGuideRepository, app.syncStatusStore)
            }
        }
    }
}
