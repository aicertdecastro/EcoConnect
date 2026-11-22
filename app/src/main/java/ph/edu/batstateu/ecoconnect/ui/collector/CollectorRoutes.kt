package ph.edu.batstateu.ecoconnect.ui.collector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.guide.PickupSchedule
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.ui.confirm.rememberConfirmPickup
import ph.edu.batstateu.ecoconnect.ui.viewmodel.CollectorViewModel

@Composable
fun CollectorHomeRoute(
    user: AuthState.SignedIn,
    onRequestClick: (requestId: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectorViewModel = viewModel(factory = CollectorViewModel.Factory),
) {
    val state by viewModel.homeState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CollectorTab.New) }
    val snackbar = remember { SnackbarHostState() }

    // Refresh whenever Collector Home opens (quietly; the offline banner already explains).
    LaunchedEffect(Unit) { viewModel.refresh(showErrors = false) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    CollectorHomeScreen(
        user = user,
        state = state,
        selectedTab = tab,
        onTabSelected = { tab = it },
        onRefresh = { viewModel.refresh() },
        onRequestClick = onRequestClick,
        onSignOut = onSignOut,
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

@Composable
fun CollectorRequestDetailsRoute(
    requestId: String,
    onBack: () -> Unit,
    onConfirmPickup: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectorViewModel = viewModel(factory = CollectorViewModel.Factory),
) {
    val flow = remember(requestId) { viewModel.request(requestId) }
    val request by produceState<CollectionRequestEntity?>(null, flow) { flow.collect { value = it } }
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val responding by viewModel.responding.collectAsStateWithLifecycle()
    val guide by viewModel.guide.collectAsStateWithLifecycle()
    val receiptFlow = remember(requestId) { viewModel.receipt(requestId) }
    val receipt by receiptFlow.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Online when there is internet; Bluetooth screen otherwise.
    val confirmPickup = rememberConfirmPickup(requestId, onOpenBluetooth = { onConfirmPickup(requestId) })

    // Once shown, a request that disappears (another collector took it) closes the screen.
    var wasLoaded by rememberSaveable { mutableStateOf(false) }
    val current = request
    LaunchedEffect(current == null) {
        if (current != null) wasLoaded = true else if (wasLoaded) onBack()
    }
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    CollectorRequestDetailsScreen(
        request = current,
        isOnline = isOnline,
        responding = responding,
        isDateAllowed = { millis -> guide?.let { PickupSchedule.isProposalDateAllowed(millis, current.wasteItems, it) } ?: false },
        onRespond = { action -> viewModel.respond(current, action) },
        onConfirmPickup = confirmPickup,
        onConfirmAgainWithBluetooth = { onConfirmPickup(current.id) },
        onBack = onBack,
        snackbarHostState = snackbar,
        receipt = receipt,
        modifier = modifier,
    )
}
