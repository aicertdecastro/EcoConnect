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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.guide.CategoryIds
import ph.edu.batstateu.ecoconnect.data.guide.GuideCategory
import ph.edu.batstateu.ecoconnect.data.guide.PickupSchedule
import ph.edu.batstateu.ecoconnect.data.guide.Weekday
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import ph.edu.batstateu.ecoconnect.ui.components.label
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen800
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- UI model ----------

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

    /** Category ids with kg > 0. */
    val selectedIds: List<String> get() = categories.filter { it.kg > 0.0 }.map { it.category.id }

    val canSubmit: Boolean
        get() = address.isNotBlank() && dateMillis != null && hour != null && minute != null &&
            totalKg > 0.0 && status == SyncStatus.PENDING_SUBMISSION

    companion object {
        /** Starting form with [categoryId] pre-selected (one kg step), e.g. when coming from the guide. */
        fun preselected(categoryId: String?): RequestPickupUiState {
            val base = RequestPickupUiState()
            val category = categoryId?.let(WasteCategory::fromId) ?: return base
            return base.copy(
                categories = base.categories.map { if (it.category == category) it.copy(kg = KG_STEP) else it },
            )
        }
    }
}

internal const val KG_STEP = 0.5

/** Guide rules for the categories the user selected. */
private fun RequestPickupUiState.selectedRules(rules: List<GuideCategory>): List<GuideCategory> =
    rules.filter { it.id in selectedIds }

/** True if [utcMillis] is a collection day for at least one selected category. */
internal fun isPickupDateAllowed(utcMillis: Long, selected: List<GuideCategory>): Boolean =
    PickupSchedule.isDateAllowed(Weekday.fromUtcMillis(utcMillis), selected)

// ---------- Stateful entry point ----------

/**
 * Holds the form state locally. [rules] come from the waste guide and decide which
 * categories can be requested and which dates are allowed. [onSubmit] receives the filled form.
 */
@Composable
fun RequestPickupScreen(
    rules: List<GuideCategory>,
    onBack: () -> Unit,
    onSubmit: (RequestPickupUiState) -> Unit,
    modifier: Modifier = Modifier,
    initialState: RequestPickupUiState = RequestPickupUiState(),
) {
    var state by remember { mutableStateOf(initialState) }
    RequestPickupContent(
        state = state,
        rules = rules,
        onBack = onBack,
        onAddressChange = { state = state.copy(address = it) },
        onDateChange = { state = state.copy(dateMillis = it) },
        onTimeChange = { h, m -> state = state.copy(hour = h, minute = m) },
        onCategoryKgChange = { category, kg ->
            val updated = state.copy(
                categories = state.categories.map { if (it.category == category) it.copy(kg = kg) else it },
            )
            // Drop the date if it no longer matches any selected category's collection days.
            val date = updated.dateMillis
            state = if (date != null && !isPickupDateAllowed(date, updated.selectedRules(rules))) {
                updated.copy(dateMillis = null)
            } else {
                updated
            }
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
    rules: List<GuideCategory>,
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
    val selected = state.selectedRules(rules)

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

            SectionLabel("Waste category summary")
            CategorySummaryCard(
                categories = state.categories,
                rules = rules,
                totalKg = state.totalKg,
                editable = editable,
                onCategoryKgChange = onCategoryKgChange,
            )

            SectionLabel("Pickup date & time")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    value = state.dateMillis?.let(::formatDate) ?: "Select date",
                    isPlaceholder = state.dateMillis == null,
                    icon = Icons.Outlined.CalendarMonth,
                    contentDescription = "Pick date",
                    enabled = editable && selected.isNotEmpty(),
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
            ScheduleInfo(dateMillis = state.dateMillis, selected = selected)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        PickupDatePickerDialog(
            initialMillis = state.dateMillis,
            isDateAllowed = { isPickupDateAllowed(it, selected) },
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

/** Helper text under the date field: what the chosen day covers, plus schedule notes. */
@Composable
private fun ScheduleInfo(dateMillis: Long?, selected: List<GuideCategory>) {
    val lines = buildList {
        if (selected.isEmpty()) {
            add("Pick waste types first; dates follow the collection schedule.")
        } else if (dateMillis != null) {
            val day = Weekday.fromUtcMillis(dateMillis)
            val coverage = PickupSchedule.coverage(day, selected)
            if (coverage.collected.isNotEmpty()) {
                add("Collected on ${day.label}: ${coverage.collected.joinToString { it.name }}")
            }
            if (coverage.notCollected.isNotEmpty()) {
                add("Not collected on ${day.label}: ${coverage.notCollected.joinToString { it.name }}")
            }
        } else {
            selected.filter { it.collectionDays.isNotEmpty() }.forEach { c ->
                add("${c.name}: ${c.collectionDays.joinToString { it.shortLabel }}")
            }
        }
        selected.firstOrNull { it.id == CategoryIds.RESIDUAL }?.scheduleNote?.let { add(it) }
    }
    if (lines.isEmpty()) return
    Row(Modifier.padding(top = 8.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp, end = 8.dp)
                .size(16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
        modifier = modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.6f),
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
    rules: List<GuideCategory>,
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
                val rule = rules.firstOrNull { it.id == item.category.id }
                val requestable = rule?.requestable ?: true
                val subtitle = when {
                    !requestable -> "Not collected door-to-door — see Waste Guide"
                    rule == null -> null
                    rule.collectionDays.isEmpty() -> "No published day"
                    else -> rule.collectionDays.joinToString { it.shortLabel }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (requestable) 1f else 0.55f)
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(rule?.name ?: item.category.label, style = MaterialTheme.typography.bodyMedium)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (requestable) {
                        IconButton(
                            onClick = { onCategoryKgChange(item.category, (item.kg - KG_STEP).coerceAtLeast(0.0)) },
                            enabled = editable && item.kg > 0.0,
                            modifier = Modifier.size(32.dp),
                        ) { Icon(Icons.Outlined.Remove, contentDescription = "Less ${item.category.label}") }
                        Text(
                            "${formatKg(item.kg)} kg",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(56.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { onCategoryKgChange(item.category, item.kg + KG_STEP) },
                            enabled = editable,
                            modifier = Modifier.size(32.dp),
                        ) { Icon(Icons.Outlined.Add, contentDescription = "More ${item.category.label}") }
                    }
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
            rules = FakeData.guideCategories,
            onBack = {}, onAddressChange = {}, onDateChange = {}, onTimeChange = { _, _ -> },
            onCategoryKgChange = { _, _ -> }, onSubmit = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Request Pickup – From guide (nothing chosen)")
@Composable
private fun RequestPickupEmptyPreview() {
    EcoConnectTheme {
        RequestPickupContent(
            state = RequestPickupUiState.preselected(null),
            rules = FakeData.guideCategories,
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
            rules = FakeData.guideCategories,
            onBack = {}, onAddressChange = {}, onDateChange = {}, onTimeChange = { _, _ -> },
            onCategoryKgChange = { _, _ -> }, onSubmit = {},
        )
    }
}
