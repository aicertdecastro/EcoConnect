package ph.edu.batstateu.ecoconnect.ui.confirm

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollected

/**
 * "Confirm pickup" for [requestId]: shows the online dialogs and returns the click handler.
 * With no internet the click opens Bluetooth ([onOpenBluetooth]) straight away.
 */
@Composable
fun rememberConfirmPickup(
    requestId: String,
    onOpenBluetooth: () -> Unit,
    viewModel: OnlineConfirmViewModel = viewModel(factory = OnlineConfirmViewModel.Factory),
): () -> Unit {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConfirmPickupDialog(
        state = state,
        onConfirm = { viewModel.confirm(requestId) },
        onDismiss = viewModel::dismiss,
        onUseBluetooth = {
            viewModel.dismiss()
            onOpenBluetooth()
        },
    )
    return {
        if (viewModel.onConfirmClicked() == ConfirmRoute.Bluetooth) onOpenBluetooth()
    }
}

@Composable
fun ConfirmPickupDialog(
    state: OnlineConfirmState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUseBluetooth: () -> Unit,
) {
    when (state) {
        OnlineConfirmState.Idle -> Unit
        OnlineConfirmState.Asking -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.CloudDone, contentDescription = null) },
            title = { Text("Confirm this pickup?") },
            text = { Text("The waste was handed over. This is saved online right away and the request becomes Collected.") },
            confirmButton = { Button(onClick = onConfirm) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        OnlineConfirmState.Saving -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Confirming…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Saving online", modifier = Modifier.padding(start = 16.dp))
                }
            },
            confirmButton = {},
        )
        OnlineConfirmState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = StatusCollected) },
            title = { Text("Pickup confirmed ✓") },
            text = { Text("The receipt is saved online.") },
            confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
        )
        is OnlineConfirmState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.CloudOff, contentDescription = null) },
            title = { Text("Not confirmed") },
            text = { Text(state.message) },
            confirmButton = { Button(onClick = onUseBluetooth) { Text("Use Bluetooth") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    }
}

@Preview(name = "Confirm online – ask")
@Composable
private fun AskPreview() = EcoConnectTheme { ConfirmPickupDialog(OnlineConfirmState.Asking, {}, {}, {}) }

@Preview(name = "Confirm online – saving")
@Composable
private fun SavingPreview() = EcoConnectTheme { ConfirmPickupDialog(OnlineConfirmState.Saving, {}, {}, {}) }

@Preview(name = "Confirm online – done")
@Composable
private fun DonePreview() = EcoConnectTheme { ConfirmPickupDialog(OnlineConfirmState.Done, {}, {}, {}) }

@Preview(name = "Confirm online – failed")
@Composable
private fun FailedPreview() = EcoConnectTheme {
    ConfirmPickupDialog(OnlineConfirmState.Failed("Couldn't reach the server. Confirm with Bluetooth instead."), {}, {}, {})
}
