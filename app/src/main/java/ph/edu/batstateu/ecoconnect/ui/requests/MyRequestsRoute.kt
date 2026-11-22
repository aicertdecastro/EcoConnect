package ph.edu.batstateu.ecoconnect.ui.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.ui.confirm.rememberConfirmPickup
import ph.edu.batstateu.ecoconnect.ui.viewmodel.RequestViewModel

@Composable
fun MyRequestsRoute(
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestViewModel = viewModel(factory = RequestViewModel.Factory),
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsStateWithLifecycle()

    MyRequestsScreen(
        requests = requests,
        pendingCount = pendingCount,
        isSyncing = isSyncing,
        lastSyncedAt = lastSyncedAt,
        onRetryNow = viewModel::retryNow,
        onRequestClick = onRequestClick,
        modifier = modifier,
    )
}

/** Live details for one request; goes back if the request no longer exists. */
@Composable
fun RequestDetailsRoute(
    requestId: String,
    onBack: () -> Unit,
    onConfirmPickup: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestViewModel = viewModel(factory = RequestViewModel.Factory),
) {
    val flow = remember(requestId) { viewModel.request(requestId) }
    val state by produceState<LoadState>(LoadState.Loading, flow) {
        flow.collect { value = if (it == null) LoadState.Missing else LoadState.Loaded(it) }
    }
    val receiptFlow = remember(requestId) { viewModel.receipt(requestId) }
    val receipt by receiptFlow.collectAsStateWithLifecycle(initialValue = null)
    // Online when there is internet; Bluetooth screen otherwise.
    val confirmPickup = rememberConfirmPickup(requestId, onOpenBluetooth = { onConfirmPickup(requestId) })

    when (val s = state) {
        LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LoadState.Missing -> LaunchedEffect(Unit) { onBack() }
        is LoadState.Loaded -> RequestDetailsScreen(
            request = s.request,
            receipt = receipt,
            onConfirmPickup = confirmPickup,
            onConfirmAgainWithBluetooth = { onConfirmPickup(requestId) },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data object Missing : LoadState
    data class Loaded(val request: CollectionRequestEntity) : LoadState
}
