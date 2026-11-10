package ph.edu.batstateu.ecoconnect.ui.pickup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.components.label
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen100
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen800
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import ph.edu.batstateu.ecoconnect.ui.theme.TileAmber
import ph.edu.batstateu.ecoconnect.ui.theme.TileBlue
import ph.edu.batstateu.ecoconnect.ui.theme.TileTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- UI model ----------

enum class WasteCategory(val label: String, val icon: ImageVector, val accent: Color) {
    Biodegradable("Biodegradable", Icons.Outlined.Eco, EcoGreen100),
    Recyclable("Recyclable", Icons.Outlined.Recycling, TileBlue),
    Residual("Residual", Icons.Outlined.Delete, TileTeal),
    Special("Special / Hazardous", Icons.Outlined.Warning, TileAmber),
}

data class CategoryAmount(val category: WasteCategory, val kg: Double)

data class RequestPickupUiState(
    val address: String = "",
    /** UTC midnight millis from the M3 DatePicker. */
    val dateMillis: Long? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val categories: List<CategoryAmount> = WasteCategory.entries.map { CategoryAmount(it, 0.0) },
    val status: SyncStatus = SyncStatus.PENDING_SUBMISSION,
) {
    val totalKg: Double get() = categories.sumOf { it.kg }
    val canSubmit: Boolean
        get() = address.isNotBlank() && dateMillis != null && hour != null && minute != null &&
            totalKg > 0.0 && status == SyncStatus.PENDING_SUBMISSION
}

private const val KG_STEP = 0.5

// ---------- Stateful entry point (used by navigation) ----------

/**
 * Holds the form state locally. [onSubmit] receives the filled form; saving it
 * (Room → Supabase sync) will be wired in later.
 */
@Composable
fun RequestPickupScreen(
    onBack: () -> Unit,
    onSubmit: (RequestPickupUiState) -> Unit,
    modifier: Modifier = Modifier,
    initialState: RequestPickupUiState = RequestPickupUiState(),
) {
    var state by remember { mutableStateOf(initialState) }
    RequestPickupContent(
        state = state,
        onBack = onBack,
        onAddressChange = { state = state.copy(address = it) },
        onDateChange = { state = state.copy(dateMillis = it) },
        onTimeChange = { h, m -> state = state.copy(hour = h, minute = m) },
        onCategoryKgChange = { category, kg ->
            state = state.copy(
                categories = state.categories.map { if (it.category == category) it.copy(kg = kg) else it },
            )
        },
        onSubmit = { onSubmit(state) },
        modifier = modifier,
    )
}

// ---------- Stateless content ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPickupContent(
    state: RequestPickupUiState,
    onBack: () -> Unit,
    onAddressChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onCategoryKgChange: (WasteCategory, Double) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val editable = state.status == SyncStatus.PENDING_SUBMISSION

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Request Pickup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            SubmitBar(
                enabled = state.canSubmit,
                label = if (editable) "Submit Request" else "Request ${state.status.label.lowercase()}",
                onSubmit = onSubmit,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Pickup details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SyncStatusChip(state.status)
            }

            SectionLabel("Pickup address")
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("House no., street, barangay, city") },
                leadingIcon = {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                shape = MaterialTheme.shapes.small,
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            SectionLabel("Pickup date & time")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    value = state.dateMillis?.let(::formatDate) ?: "Select date",
                    isPlaceholder = state.dateMillis == null,
                    icon = Icons.Outlined.CalendarMonth,
                    contentDescription = "Pick date",
                    enabled = editable,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.4f),
                )
                PickerField(
                    value = if (state.hour != null && state.minute != null) formatTime(state.hour, state.minute) else "Time",
                    isPlaceholder = state.hour == null,
                    icon = Icons.Outlined.Schedule,
                    contentDescription = "Pick time",
                    enabled = editable,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel("Waste category summary")
            CategorySummaryCard(
                categories = state.categories,
                totalKg = state.totalKg,
                editable = editable,
                onCategoryKgChange = onCategoryKgChange,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        PickupDatePickerDialog(
            initialMillis = state.dateMillis,
            onConfirm = {
                onDateChange(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        PickupTimePickerDialog(
            initialHour = state.hour ?: 8,
            initialMinute = state.minute ?: 0,
            onConfirm = { h, m ->
                onTimeChange(h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Field-shaped button that opens a picker dialog. */
@Composable
private fun PickerField(
    value: String,
    isPlaceholder: Boolean,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CategorySummaryCard(
    categories: List<CategoryAmount>,
    totalKg: Double,
    editable: Boolean,
    onCategoryKgChange: (WasteCategory, Double) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            categories.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(item.category.accent, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.category.icon, contentDescription = null, tint = EcoGreen800, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        item.category.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    IconButton(
                        onClick = { onCategoryKgChange(item.category, (item.kg - KG_STEP).coerceAtLeast(0.0)) },
                        enabled = editable && item.kg > 0.0,
                        modifier = Modifier.size(32.dp),
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Less ${item.category.label}") }
                    Text(
                        "${formatKg(item.kg)} kg",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(56.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(
                        onClick = { onCategoryKgChange(item.category, item.kg + KG_STEP) },
                        enabled = editable,
                        modifier = Modifier.size(32.dp),
                    ) { Icon(Icons.Outlined.Add, contentDescription = "More ${item.category.label}") }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Estimated total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${formatKg(totalKg)} kg",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SubmitBar(enabled: Boolean, label: String, onSubmit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onSubmit,
            enabled = enabled,
            shape = PillShape,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(56.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickupTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select pickup time") },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

// ---------- Formatting ----------

private fun formatDate(utcMillis: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(utcMillis))

private fun formatTime(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val period = if (hour < 12) "AM" else "PM"
    return String.format(Locale.US, "%d:%02d %s", h12, minute, period)
}

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

// ---------- Previews (fake data only) ----------

@Preview(showSystemUi = true, name = "Request Pickup – Pending")
@Composable
private fun RequestPickupPendingPreview() {
    EcoConnectTheme {
        RequestPickupContent(
            state = FakeData.pickupState,
            onBack = {}, onAddressChange = {}, onDateChange = {}, onTimeChange = { _, _ -> },
            onCategoryKgChange = { _, _ -> }, onSubmit = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Request Pickup – Accepted")
@Composable
private fun RequestPickupAcceptedPreview() {
    EcoConnectTheme {
        RequestPickupContent(
            state = FakeData.pickupState.copy(status = SyncStatus.ACCEPTED),
            onBack = {}, onAddressChange = {}, onDateChange = {}, onTimeChange = { _, _ -> },
            onCategoryKgChange = { _, _ -> }, onSubmit = {},
        )
    }
}
