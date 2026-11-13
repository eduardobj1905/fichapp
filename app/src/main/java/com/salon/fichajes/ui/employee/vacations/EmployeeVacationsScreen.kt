package com.salon.fichajes.ui.employee.vacations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import com.salon.fichajes.ui.components.EmptyMessage
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.StatCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeVacationsScreen(
    onBack: () -> Unit,
    viewModel: EmployeeVacationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis vacaciones ${state.year}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Solicitar") },
                expanded = !state.isSubmitting,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Te quedan", "${state.balance.remaining} días", "de ${state.balance.annual} al año", Modifier.weight(1f))
                StatCard("Aprobados", "${state.balance.approved}", null, Modifier.weight(1f))
                StatCard("Pendientes", "${state.balance.pending}", null, Modifier.weight(1f))
            }
            Text("Mis solicitudes", style = MaterialTheme.typography.titleMedium)
            if (state.requests.isEmpty()) {
                EmptyMessage("Aún no has pedido vacaciones.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.requests, key = { it.id }) { RequestRow(it) }
                }
            }
        }
    }

    if (showPicker) {
        VacationRangePickerDialog(
            today = viewModel.today,
            onDismiss = { showPicker = false },
            onConfirm = { start, end ->
                showPicker = false
                viewModel.request(start, end)
            },
        )
    }
}

@Composable
private fun RequestRow(request: VacationRequest) {
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("${request.startDate.formatShort()} → ${request.endDate.formatShort()}") },
            supportingContent = {
                Column {
                    Text("${request.days} días naturales")
                    request.adminComment?.let { Text("Comentario: $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            trailingContent = { StatusChip(request.status) },
        )
    }
}

@Composable
fun StatusChip(status: VacationStatus) {
    val text = when (status) {
        VacationStatus.PENDING -> "Pendiente"
        VacationStatus.APPROVED -> "Aprobada"
        VacationStatus.REJECTED -> "Rechazada"
    }
    AssistChip(onClick = {}, label = { Text(text) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacationRangePickerDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    // El DateRangePicker trabaja en milisegundos UTC a medianoche: convertimos con ZoneOffset.UTC.
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
        },
    )
    fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val start = state.selectedStartDateMillis
            val end = state.selectedEndDateMillis ?: start
            TextButton(
                enabled = start != null,
                onClick = { if (start != null && end != null) onConfirm(start.toLocalDate(), end.toLocalDate()) },
            ) { Text("Solicitar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Elige las fechas", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = false,
            modifier = Modifier.weight(1f).widthIn(max = 560.dp).align(Alignment.CenterHorizontally),
        )
    }
}
