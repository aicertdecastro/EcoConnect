package ph.edu.batstateu.ecoconnect.ui.pickup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.ui.viewmodel.RequestViewModel

/**
 * Connects [RequestPickupScreen] to Room and the waste guide.
 * [preselectedCategoryId] (e.g. "RECYCLABLE" from a guide result) starts selected.
 */
@Composable
fun RequestPickupRoute(
    onBack: () -> Unit,
    onSubmitted: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    preselectedCategoryId: String? = null,
    viewModel: RequestViewModel = viewModel(factory = RequestViewModel.Factory),
) {
    val guide by viewModel.guide.collectAsStateWithLifecycle()
    val loaded = guide
    if (loaded == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Only requestable categories can be pre-selected.
    val preselect = preselectedCategoryId?.takeIf { id -> loaded.category(id)?.requestable == true }

    RequestPickupScreen(
        rules = loaded.categories,
        initialState = RequestPickupUiState.preselected(preselect),
        onBack = onBack,
        onSubmit = { form -> viewModel.submit(form, onSaved = onSubmitted) },
        modifier = modifier,
    )
}
