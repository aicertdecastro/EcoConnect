package ph.edu.batstateu.ecoconnect.ui.pickup

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import java.util.Calendar
import java.util.TimeZone

/**
 * Material 3 date picker for choosing a pickup day. Days before today can't be selected,
 * and [isDateAllowed] can restrict further (e.g. to the collection schedule).
 * [initialMillis] and the value passed to [onConfirm] are UTC midnight millis, as M3 DatePicker uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupDatePickerDialog(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    isDateAllowed: (utcMillis: Long) -> Boolean = { true },
) {
    val todayUtc = remember { todayUtcMidnightMillis() }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis?.takeIf { it >= todayUtc && isDateAllowed(it) },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis >= todayUtc && isDateAllowed(utcTimeMillis)

            override fun isSelectableYear(year: Int) = year >= currentYear
        },
    )
    val colors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.primary,
        headlineContentColor = MaterialTheme.colorScheme.onSurface,
        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
        todayDateBorderColor = MaterialTheme.colorScheme.primary,
        todayContentColor = MaterialTheme.colorScheme.primary,
        selectedYearContainerColor = MaterialTheme.colorScheme.primary,
        selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onConfirm) },
                enabled = state.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = MaterialTheme.shapes.large,
        colors = colors,
    ) {
        DatePicker(
            state = state,
            colors = colors,
            title = {
                Text(
                    "Select pickup date",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/** UTC midnight of today's local calendar date — the format M3 DatePicker compares against. */
private fun todayUtcMidnightMillis(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@Preview(showSystemUi = true)
@Composable
private fun PickupDatePickerDialogPreview() {
    EcoConnectTheme {
        // Recyclable only: Tuesdays, Thursdays and Saturdays are selectable.
        val recyclable = FakeData.guideCategories.filter { it.id == "RECYCLABLE" }
        PickupDatePickerDialog(
            initialMillis = null,
            isDateAllowed = { isPickupDateAllowed(it, recyclable) },
            onConfirm = {},
            onDismiss = {},
        )
    }
}
