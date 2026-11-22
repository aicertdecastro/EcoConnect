package ph.edu.batstateu.ecoconnect.ui.bluetooth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.bluetooth.BtDevice
import ph.edu.batstateu.ecoconnect.bluetooth.BtRole
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.requests.formatPickupDate
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollected
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollectedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclined
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclinedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPendingContainer
import java.text.SimpleDateFormat
import java.util.Locale

/** Everything the screen can ask for. */
data class BtConfirmActions(
    val onBack: () -> Unit = {},
    val onGrantPermission: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onTurnOnBluetooth: () -> Unit = {},
    val onMakeVisible: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onDeviceClick: (BtDevice) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onChooseAnotherPhone: () -> Unit = {},
    val onDone: () -> Unit = {},
)

/** Bluetooth pickup confirmation for both roles. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtConfirmScreen(
    role: BtRole,
    referenceCode: String,
    request: CollectionRequestEntity?,
    state: BtConfirmUiState,
    actions: BtConfirmActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Confirm pickup") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReferenceCard(role, referenceCode, request)
            when (state) {
                BtConfirmUiState.Idle -> Busy("Checking Bluetooth…")
                is BtConfirmUiState.Setup -> SetupContent(role, state.need, actions)
                is BtConfirmUiState.CollectorWaiting -> CollectorWaitingContent(state, now, actions)
                is BtConfirmUiState.PickDevice -> PickDeviceContent(state, actions)
                is BtConfirmUiState.Connecting -> Busy("Connecting to ${state.deviceName}…", "Keep both phones close together.")
                BtConfirmUiState.Verifying -> Busy("Verifying…", "Checking the request on both phones.")
                is BtConfirmUiState.Confirmed -> ConfirmedContent(role, referenceCode, state, actions)
                is BtConfirmUiState.Rejected -> ResultContent(
                    icon = Icons.Outlined.Block,
                    tint = StatusDeclined,
                    container = StatusDeclinedContainer,
                    title = "Confirmation rejected",
                    message = state.reason,
                    primary = "Close" to actions.onDone,
                    secondary = if (role == BtRole.USER) "Choose another phone" to actions.onChooseAnotherPhone else null,
                )
                is BtConfirmUiState.Incomplete -> ResultContent(
                    icon = Icons.Outlined.ErrorOutline,
                    tint = StatusPending,
                    container = StatusPendingContainer,
                    title = "Confirmation incomplete",
                    message = state.reason + "\nNothing was marked as collected. Try again.",
                    primary = "Retry" to actions.onRetry,
                    secondary = if (role == BtRole.USER) "Choose another phone" to actions.onChooseAnotherPhone else null,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReferenceCard(role: BtRole, referenceCode: String, request: CollectionRequestEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Reference code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                referenceCode,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Both phones must show this same code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (request != null) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                val who = if (role == BtRole.COLLECTOR) request.requesterName ?: "Resident" else request.collectorName ?: "Your collector"
                Text(
                    "$who · ${formatPickupDate(request.proposedDate ?: request.pickupDate)} · ${request.pickupTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Busy(title: String, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun SetupContent(role: BtRole, need: BtSetupNeed, actions: BtConfirmActions) {
    val why = if (role == BtRole.COLLECTOR) {
        "EcoConnect uses Bluetooth to let the resident's phone connect to yours and confirm the pickup. It works without internet."
    } else {
        "EcoConnect uses Bluetooth to find the collector's phone nearby and confirm the pickup. It works without internet and never uses your location."
    }
    when (need) {
        BtSetupNeed.NoAdapter -> ResultContent(
            icon = Icons.Outlined.BluetoothDisabled,
            tint = StatusDeclined,
            container = StatusDeclinedContainer,
            title = "Bluetooth not available on this device",
            message = "This phone has no Bluetooth, so it can't confirm pickups. Use a phone with Bluetooth.",
            primary = "Close" to actions.onBack,
        )
        BtSetupNeed.Permission -> ResultContent(
            icon = Icons.Outlined.Bluetooth,
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            title = "Allow Bluetooth",
            message = why,
            primary = "Allow Bluetooth" to actions.onGrantPermission,
        )
        BtSetupNeed.PermissionDenied -> ResultContent(
            icon = Icons.Outlined.Bluetooth,
            tint = StatusPending,
            container = StatusPendingContainer,
            title = "Bluetooth permission needed",
            message = "Without it, the pickup can't be confirmed. $why",
            primary = "Try again" to actions.onGrantPermission,
        )
        BtSetupNeed.PermissionPermanentlyDenied -> ResultContent(
            icon = Icons.Outlined.Settings,
            tint = StatusPending,
            container = StatusPendingContainer,
            title = "Turn on Bluetooth permission in Settings",
            message = "The permission was denied. Open Settings › Permissions › Nearby devices and allow it, then come back.",
            primary = "Open settings" to actions.onOpenSettings,
        )
        BtSetupNeed.BluetoothOff -> ResultContent(
            icon = Icons.Outlined.BluetoothDisabled,
            tint = StatusPending,
            container = StatusPendingContainer,
            title = "Bluetooth is off",
            message = "Turn on Bluetooth to confirm the pickup.",
            primary = "Turn on Bluetooth" to actions.onTurnOnBluetooth,
        )
    }
}

@Composable
private fun CollectorWaitingContent(state: BtConfirmUiState.CollectorWaiting, now: Long, actions: BtConfirmActions) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Outlined.BluetoothSearching,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .padding(16.dp),
        )
        Text("Waiting for resident's phone…", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        LinearProgressIndicator(Modifier.fillMaxWidth(0.6f))
        Text(
            "Ask the resident to open their request, tap \"Confirm pickup\" and choose this phone:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(state.phoneName ?: "This phone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
        }
        val secondsLeft = state.visibleUntil?.let { ((it - now) / 1000).coerceAtLeast(0) } ?: 0
        if (secondsLeft > 0) {
            Text("Visible to nearby phones for $secondsLeft s", style = MaterialTheme.typography.bodySmall, color = StatusCollected)
        } else {
            Text(
                "Not paired yet? Make this phone visible so the resident can find it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(onClick = actions.onMakeVisible, shape = PillShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Make this phone visible", modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = actions.onCancel) { Text("Cancel") }
    }
}

@Composable
private fun PickDeviceContent(state: BtConfirmUiState.PickDevice, actions: BtConfirmActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Ask the collector to open this pickup and tap \"Confirm pickup\" (with no internet it opens Bluetooth). Then choose their phone:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeviceSection("Paired phones", state.paired, "No paired phones yet. Scan for the collector's phone below.", actions.onDeviceClick)
        if (state.found.isNotEmpty() || state.scanning) {
            DeviceSection("Nearby phones", state.found, if (state.scanning) "Looking…" else "None found.", actions.onDeviceClick)
        }
        OutlinedButton(
            onClick = actions.onScan,
            enabled = !state.scanning,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.scanning) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Scanning for nearby devices…", modifier = Modifier.padding(start = 8.dp))
            } else {
                Icon(Icons.Outlined.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Scan for nearby devices", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "The collector's phone must be visible to show up in a scan. The first time, both phones ask to pair: accept on both.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceSection(title: String, devices: List<BtDevice>, empty: String, onClick: (BtDevice) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (devices.isEmpty()) {
                Text(
                    empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            devices.forEach { device ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick(device) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (device.paired) "Paired" else device.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmedContent(role: BtRole, referenceCode: String, state: BtConfirmUiState.Confirmed, actions: BtConfirmActions) {
    val date = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(state.confirmedAt)
    val time = SimpleDateFormat("h:mm:ss a", Locale.US).format(state.confirmedAt)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = StatusCollected,
            modifier = Modifier
                .size(88.dp)
                .background(StatusCollectedContainer, CircleShape)
                .padding(16.dp),
        )
        Text("Confirmed ✓", style = MaterialTheme.typography.headlineSmall, color = StatusCollected)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Reference", referenceCode)
                Field("Date", date)
                Field("Time", time)
                Field("Confirmed in", formatSeconds(state.durationMs))
            }
        }
        Text(
            if (role == BtRole.COLLECTOR) "Marked as collected. The receipt uploads when this phone has internet."
            else "Your pickup is marked as collected. The receipt uploads when this phone has internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state.missingDone) {
            Text(
                "The collector's phone didn't reply at the end. If it doesn't show Collected, confirm again: no duplicate will be made.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusPending,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = actions.onDone, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
    }
}

@Composable
private fun ResultContent(
    icon: ImageVector,
    tint: Color,
    container: Color,
    title: String,
    message: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(80.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = primary.second, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(primary.first) }
        secondary?.let { (label, onClick) ->
            OutlinedButton(onClick = onClick, shape = PillShape, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(label) }
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

/** 2345 → "2.3 s". */
internal fun formatSeconds(millis: Long): String = String.format(Locale.US, "%.1f s", millis.coerceAtLeast(0) / 1000.0)

// ---------- Previews ----------

private val previewRequest get() = FakeData.requests[3]
private const val PREVIEW_CODE = "EC-3F9A1C"
private const val PREVIEW_NOW = 1_795_300_000_000L

@Composable
private fun Preview(role: BtRole, state: BtConfirmUiState) {
    EcoConnectTheme { BtConfirmScreen(role, PREVIEW_CODE, previewRequest, state, BtConfirmActions(), now = PREVIEW_NOW) }
}

@Preview(showSystemUi = true, name = "BT – permission")
@Composable
private fun PermissionPreview() = Preview(BtRole.USER, BtConfirmUiState.Setup(BtSetupNeed.Permission))

@Preview(showSystemUi = true, name = "BT – permanently denied")
@Composable
private fun DeniedPreview() = Preview(BtRole.USER, BtConfirmUiState.Setup(BtSetupNeed.PermissionPermanentlyDenied))

@Preview(showSystemUi = true, name = "BT – off")
@Composable
private fun OffPreview() = Preview(BtRole.COLLECTOR, BtConfirmUiState.Setup(BtSetupNeed.BluetoothOff))

@Preview(showSystemUi = true, name = "BT – no adapter")
@Composable
private fun NoAdapterPreview() = Preview(BtRole.USER, BtConfirmUiState.Setup(BtSetupNeed.NoAdapter))

@Preview(showSystemUi = true, name = "BT – collector waiting")
@Composable
private fun WaitingPreview() = Preview(BtRole.COLLECTOR, BtConfirmUiState.CollectorWaiting("Pixel 8 (Collector)", visibleUntil = PREVIEW_NOW + 95_000))

@Preview(showSystemUi = true, name = "BT – device picker")
@Composable
private fun PickerPreview() = Preview(
    BtRole.USER,
    BtConfirmUiState.PickDevice(
        paired = listOf(BtDevice("Collector Juan's phone", "AA:BB:CC:00:11:22", true)),
        found = listOf(BtDevice("Galaxy A15", "AA:BB:CC:33:44:55", false)),
        scanning = true,
    ),
)

@Preview(showSystemUi = true, name = "BT – connecting")
@Composable
private fun ConnectingPreview() = Preview(BtRole.USER, BtConfirmUiState.Connecting("Collector Juan's phone"))

@Preview(showSystemUi = true, name = "BT – verifying")
@Composable
private fun VerifyingPreview() = Preview(BtRole.COLLECTOR, BtConfirmUiState.Verifying)

@Preview(showSystemUi = true, name = "BT – confirmed")
@Composable
private fun ConfirmedPreview() = Preview(BtRole.USER, BtConfirmUiState.Confirmed(PREVIEW_NOW, 2_340))

@Preview(showSystemUi = true, name = "BT – rejected")
@Composable
private fun RejectedPreview() = Preview(BtRole.USER, BtConfirmUiState.Rejected("This request is assigned to another collector."))

@Preview(showSystemUi = true, name = "BT – incomplete")
@Composable
private fun IncompletePreview() = Preview(BtRole.COLLECTOR, BtConfirmUiState.Incomplete("The other phone stopped answering (no reply in 10 s)."))
