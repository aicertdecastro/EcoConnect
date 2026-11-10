package ph.edu.batstateu.ecoconnect.ui.requests

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.data.repository.SyncResult
import ph.edu.batstateu.ecoconnect.ui.viewmodel.RequestViewModel

@Composable
fun MyRequestsRoute(
    modifier: Modifier = Modifier,
    viewModel: RequestViewModel = viewModel(factory = RequestViewModel.Factory),
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    MyRequestsScreen(
        requests = requests,
        pendingCount = pendingCount,
        syncing = syncing,
        onSyncNow = {
            viewModel.syncNow { result ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(result.message())
                }
            }
        },
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

internal fun SyncResult.message(): String = when {
    offline -> "You're offline. Requests are saved on this phone and will send later."
    failed > 0 -> "Sent $pushed · $failed failed" + (error?.let { " — $it" } ?: "")
    error != null -> "Sent $pushed · couldn't refresh — $error"
    else -> "Sent $pushed · updated $refreshed"
}
