package org.screenconsume.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.screenconsume.app.R
import org.screenconsume.app.domain.model.DateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun exportDateRange(startMillis: Long?, endMillis: Long?, today: LocalDate): DateRange? {
    if (startMillis == null || endMillis == null) return null
    val start = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val end = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return if (start > end || end > today) null else DateRange(start, end)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportRangeDialog(range: DateRange, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    val today = LocalDate.now()
    val dates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    }
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = range.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = range.endInclusive.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = dates,
    )
    val selected = exportDateRange(picker.selectedStartDateMillis, picker.selectedEndDateMillis, today)
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let(onConfirm) }) {
                Text(stringResource(R.string.continue_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(picker, Modifier.fillMaxWidth().heightIn(max = 500.dp),
            title = { Text(stringResource(R.string.select_date_range), Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) },
            headline = {
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val start = picker.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(formatter) } ?: "—"
                val end = picker.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(formatter) } ?: "—"
                Text("$start – $end", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
            })
    }
}
