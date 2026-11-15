package com.salon.fichajes.ui.admin.vacations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.SpanishLocale
import com.salon.fichajes.core.format.formatMonthYear
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.staffing.DayStaffing
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.EmptyMessage
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.PeriodNavigator
import com.salon.fichajes.ui.employee.vacations.StatusChip
import com.salon.fichajes.ui.theme.ClockInGreen
import com.salon.fichajes.ui.theme.ClockOutRed
import com.salon.fichajes.ui.theme.WarningAmber
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun AdminVacationsScreen(
    snackbar: SnackbarHostState,
    viewModel: AdminVacationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var rejecting by remember { mutableStateOf<VacationRequest?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    if (state.isLoading) {
        LoadingBox()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    PeriodNavigator(state.month.formatMonthYear(), viewModel::previousMonth, viewModel::nextMonth)
                    MinStaffStepper(state.minStaff, editable = viewModel.canChangeMinStaff, onChange = viewModel::setMinStaff)
                    Spacer(Modifier.size(8.dp))
                    StaffingCalendar(state.calendar, state.minStaff)
                    Legend()
                }
            }
        }
        item { Text("Pendientes de revisar", style = MaterialTheme.typography.titleMedium) }
        if (state.pending.isEmpty()) item { EmptyMessage("No hay solicitudes pendientes.") }
        items(state.pending, key = { "p${it.id}" }) { request ->
            PendingRequestCard(
                request = request,
                employee = state.employees[request.employeeId],
                canReview = viewModel.canReview,
                onApprove = { viewModel.approve(request) },
                onReject = { rejecting = request },
            )
        }
        if (state.reviewed.isNotEmpty()) {
            item { Text("Revisadas recientemente", style = MaterialTheme.typography.titleMedium) }
            items(state.reviewed, key = { "r${it.id}" }) { request ->
                Card {
                    ListItem(
                        headlineContent = { Text(state.employees[request.employeeId]?.name ?: "Antigua empleada") },
                        supportingContent = { Text("${request.startDate.formatShort()} → ${request.endDate.formatShort()} · ${request.days} días") },
                        trailingContent = { StatusChip(request.status) },
                    )
                }
            }
        }
    }

    state.warning?.let { warning ->
        AlertDialog(
            onDismissRequest = viewModel::dismissWarning,
            title = { Text("Poco personal esos días") },
            text = {
                Text(
                    "Si apruebas, el salón quedará por debajo del mínimo de ${state.minStaff} personas en " +
                        warning.days.joinToString { "${it.date.formatShort()} (${it.available})" } + "."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmApproveDespiteWarning) { Text("Aprobar igualmente") } },
            dismissButton = { TextButton(onClick = viewModel::dismissWarning) { Text("Cancelar") } },
        )
    }

    rejecting?.let { request ->
        var comment by remember(request.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Rechazar solicitud") },
            text = { OutlinedTextField(comment, { comment = it }, label = { Text("Motivo (lo verá la empleada)") }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reject(request, comment)
                    rejecting = null
                }) { Text("Rechazar") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MinStaffStepper(value: Int, editable: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Personal mínimo por día", modifier = Modifier.weight(1f))
        if (editable) IconButton(onClick = { onChange(value - 1) }, enabled = value > 0) { Icon(Icons.Filled.Remove, "Menos") }
        Text("$value", style = MaterialTheme.typography.titleLarge)
        if (editable) IconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Filled.Add, "Más") }
    }
}

/** Calendario mensual tipo mapa de calor: verde = todo el equipo, rojo = por debajo del mínimo. */
@Composable
private fun StaffingCalendar(days: List<DayStaffing>, minStaff: Int) {
    if (days.isEmpty()) return
    val leadingBlanks = days.first().date.dayOfWeek.value - 1 // lunes = 0
    val cells: List<DayStaffing?> = List(leadingBlanks) { null } + days
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, SpanishLocale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) { if (day != null) HeatCell(day, minStaff) }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HeatCell(day: DayStaffing, minStaff: Int) {
    val below = day.available < minStaff
    val color = when {
        below -> ClockOutRed
        day.away == 0 -> ClockInGreen
        else -> WarningAmber // alguien fuera (vacaciones o baja), igual que en la leyenda
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(color.copy(alpha = if (day.away == 0 && !below) 0.25f else 0.6f), RoundedCornerShape(6.dp))
            .then(if (below) Modifier.border(2.dp, ClockOutRed, RoundedCornerShape(6.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.date.dayOfMonth}", fontWeight = FontWeight.SemiBold)
            Text("${day.available}/${day.total}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(ClockInGreen, "Todo el equipo")
        LegendDot(WarningAmber, "Alguien de vacaciones o de baja")
        LegendDot(ClockOutRed, "Bajo el mínimo")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PendingRequestCard(
    request: VacationRequest,
    employee: Employee?,
    canReview: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            employee?.let { EmployeeAvatar(it.initial, it.colorArgb, size = 40.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(employee?.name ?: "Antigua empleada", style = MaterialTheme.typography.titleMedium)
                Text("${request.startDate.formatShort()} → ${request.endDate.formatShort()} · ${request.days} días")
            }
            if (canReview) {
                OutlinedButton(onClick = onReject) { Text("Rechazar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApprove) { Text("Aprobar") }
            } else {
                // Encargada: ve las solicitudes para organizar el horario, pero las aprueba la dueña.
                Text("Pendiente de la dueña", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
