package ph.edu.batstateu.ecoconnect.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import ph.edu.batstateu.ecoconnect.ui.viewmodel.RequestViewModel

/** Connects the stateless [HomeScreen] to Room via [RequestViewModel]. */
@Composable
fun HomeRoute(
    onTileClick: (HomeTile) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestViewModel = viewModel(factory = RequestViewModel.Factory),
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()

    val stats = remember(requests, pendingCount) {
        HomeStats(
            kgSegregated = requests.filter { it.syncStatus != SyncStatus.DECLINED }.sumOf { it.totalKg },
            totalRequests = requests.size,
            pendingRequests = pendingCount,
        )
    }

    HomeScreen(stats = stats, onTileClick = onTileClick, modifier = modifier)
}
