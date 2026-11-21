package ph.edu.batstateu.ecoconnect.ui.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import ph.edu.batstateu.ecoconnect.data.repository.CollectorAction
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.pickup.PickupDatePickerDialog
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.requests.ReceiptSection
import ph.edu.batstateu.ecoconnect.ui.requests.canConfirmPickup
import ph.edu.batstateu.ecoconnect.ui.requests.categoryLabel
import ph.edu.batstateu.ecoconnect.ui.requests.formatKg
import ph.edu.batstateu.ecoconnect.ui.requests.formatMoment
import ph.edu.batstateu.ecoconnect.ui.requests.formatPickupDate
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending

/** Preset decline reasons; "Other" lets the collector type one. */
val DeclineReasons = listOf("Outside service area", "Not accepted waste", "Unavailable")

private enum class ActionDialog { None, Accept, Decline, ProposeDate, ProposeMessage }

/**
 * Full request details for collectors. New (SENT, unassigned) requests get
 * Accept / Decline / Propose date; accepted or rescheduled ones get Bluetooth confirmation.
 * [isDateAllowed] limits proposed dates to the request's collection days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectorRequestDetailsScreen(
    request: CollectionRequestEntity,
    isOnline: Boolean,
    responding: Boolean,
    isDateAllowed: (utcMillis: Long) -> Boolean,
    onRespond: (CollectorAction) -> Unit,
    onConfirmPickup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    receipt: CollectionReceiptEntity? = null,
) {
    var dialog by rememberSaveable { mutableStateOf(ActionDialog.None) }
    var proposedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val isNew = request.syncStatus == SyncStatus.SENT && request.collectorId == null

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Request") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    isNew -> NewRequestActions(
                        isOnline = isOnline,
                        responding = responding,
                        onAccept = { dialog = ActionDialog.Accept },
                        onDecline = { dialog = ActionDialog.Decline },
                        onPropose = { dialog = ActionDialog.ProposeDate },
                    )
                    canConfirmPickup(request, receipt) && receipt == null -> Button(
                        onClick = onConfirmPickup,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Confirm pickup (Bluetooth)", modifier = Modifier.padding(start = 8.dp))
                    }
                    // Already collected here: lets a retry fill a receipt missing on the resident's phone.
                    canConfirmPickup(request, receipt) -> TextButton(onClick = onConfirmPickup, modifier = Modifier.fillMaxWidth()) {
                        Text("Resident's phone not updated? Confirm again")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(request.requesterName ?: "Resident", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SyncStatusChip(request.syncStatus)
            }
            receipt?.let { ReceiptSection(it) }

            Section("Pickup") {
                Field("Date", formatPickupDate(request.pickupDate))
                Field("Time", request.pickupTime)
                Field("Address", request.address)
                request.notes?.takeIf { it.isNotBlank() }?.let { Field("Notes", it) }
            }
            Section("Waste") {
                request.wasteItems.forEach { Field(it.categoryLabel(), "${formatKg(it.kg)} kg") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Field("Total", "${formatKg(request.totalKg)} kg")
            }
            if (!isNew) {
                Section("Response") {
                    request.proposedDate?.let { Field("Proposed date", formatPickupDate(it)) }
                    request.collectorResponse?.takeIf { it.isNotBlank() }?.let {
                        Field(if (request.syncStatus == SyncStatus.DECLINED) "Reason" else "Message", it)
                    }
                    request.collectorName?.let { Field("Collector", it) }
                }
            }
            Section("Request") {
                Field("Created", formatMoment(request.createdAt))
                Field("Request ID", request.id)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    when (dialog) {
        ActionDialog.Accept -> MessageDialog(
            title = "Accept request?",
            label = "Message to resident (optional)",
            confirm = "Accept",
            requireText = false,
            onConfirm = { dialog = ActionDialog.None; onRespond(CollectorAction.Accept(it)) },
            onDismiss = { dialog = ActionDialog.None },
        )
        ActionDialog.Decline -> DeclineDialog(
            onConfirm = { dialog = ActionDialog.None; onRespond(CollectorAction.Decline(it)) },
            onDismiss = { dialog = ActionDialog.None },
        )
        ActionDialog.ProposeDate -> PickupDatePickerDialog(
            initialMillis = proposedDate,
            onConfirm = { proposedDate = it; dialog = ActionDialog.ProposeMessage },
            onDismiss = { dialog = ActionDialog.None },
            isDateAllowed = isDateAllowed,
        )
        ActionDialog.ProposeMessage -> MessageDialog(
            title = "Propose ${proposedDate?.let(::formatPickupDate) ?: "date"}?",
            label = "Message to resident (optional)",
            confirm = "Send",
            requireText = false,
            onConfirm = { msg ->
                dialog = ActionDialog.None
                proposedDate?.let { onRespond(CollectorAction.ProposeDate(it, msg)) }
            },
            onDismiss = { dialog = ActionDialog.None },
        )
        ActionDialog.None -> Unit
    }
}

@Composable
private fun NewRequestActions(
    isOnline: Boolean,
    responding: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onPropose: () -> Unit,
) {
    val enabled = isOnline && !responding
    if (!isOnline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = StatusPending, modifier = Modifier.size(18.dp))
            Text(
                "You're offline. Connect to the internet to respond.",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusPending,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    Button(
        onClick = onAccept,
        enabled = enabled,
        shape = PillShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (responding) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text("Sending…", modifier = Modifier.padding(start = 10.dp))
        } else {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Accept", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDecline, enabled = enabled, shape = PillShape, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Decline", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = onPropose, enabled = enabled, shape = PillShape, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Outlined.EventRepeat, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Propose date", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun MessageDialog(
    title: String,
    label: String,
    confirm: String,
    requireText: Boolean,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_MESSAGE) },
                label = { Text(label) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifEmpty { null }) }, enabled = !requireText || text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeclineDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var preset by rememberSaveable { mutableStateOf<String?>(null) }
    var other by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf("") }
    val reason = if (other) custom.trim() else preset.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decline request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Why are you declining? The resident will see this reason.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeclineReasons.forEach { r ->
                        FilterChip(selected = !other && preset == r, onClick = { preset = r; other = false }, label = { Text(r) })
                    }
                    FilterChip(selected = other, onClick = { other = true; preset = null }, label = { Text("Other…") })
                }
                if (other) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.take(MAX_MESSAGE) },
                        label = { Text("Reason") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotEmpty()) { Text("Decline") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}

private const val MAX_MESSAGE = 300

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "Collector details – new")
@Composable
private fun CollectorDetailsNewPreview() {
    EcoConnectTheme {
        CollectorRequestDetailsScreen(FakeData.collectorNew.first(), isOnline = true, responding = false, isDateAllowed = { true }, onRespond = {}, onConfirmPickup = {}, onBack = {})
    }
}

@Preview(showSystemUi = true, name = "Collector details – offline")
@Composable
private fun CollectorDetailsOfflinePreview() {
    EcoConnectTheme {
        CollectorRequestDetailsScreen(FakeData.collectorNew.first(), isOnline = false, responding = false, isDateAllowed = { true }, onRespond = {}, onConfirmPickup = {}, onBack = {})
    }
}

@Preview(showSystemUi = true, name = "Collector details – sending")
@Composable
private fun CollectorDetailsSendingPreview() {
    EcoConnectTheme {
        CollectorRequestDetailsScreen(FakeData.collectorNew.first(), isOnline = true, responding = true, isDateAllowed = { true }, onRespond = {}, onConfirmPickup = {}, onBack = {})
    }
}

@Preview(showSystemUi = true, name = "Collector details – accepted")
@Composable
private fun CollectorDetailsAcceptedPreview() {
    EcoConnectTheme {
        CollectorRequestDetailsScreen(FakeData.requests[3], isOnline = true, responding = false, isDateAllowed = { true }, onRespond = {}, onConfirmPickup = {}, onBack = {})
    }
}
