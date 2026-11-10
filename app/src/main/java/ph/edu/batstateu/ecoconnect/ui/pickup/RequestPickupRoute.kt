package ph.edu.batstateu.ecoconnect.ui.pickup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.ui.viewmodel.RequestViewModel

/** Connects [RequestPickupScreen] to Room: Submit saves the request locally, then [onSubmitted] runs. */
@Composable
fun RequestPickupRoute(
    onBack: () -> Unit,
    onSubmitted: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestViewModel = viewModel(factory = RequestViewModel.Factory),
) {
    RequestPickupScreen(
        onBack = onBack,
        onSubmit = { form -> viewModel.submit(form, onSaved = onSubmitted) },
        modifier = modifier,
    )
}
