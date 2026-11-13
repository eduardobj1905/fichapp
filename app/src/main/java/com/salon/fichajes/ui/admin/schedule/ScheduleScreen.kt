package com.salon.fichajes.ui.admin.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatDecimalHours
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.EmptyMessage
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.PeriodNavigator
import java.time.LocalDate

/** Vista semanal sencilla: una fila por empleada y una celda por día. Toca una celda para editar. */
@Composable
fun ScheduleScreen(
    snackbar: SnackbarHostState,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<Employee, LocalDate>?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    if (state.isLoading) {
        LoadingBox()
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PeriodNavigator(
            label = "${state.days.first().formatShort()} – ${state.days.last().formatShort()}",
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
        )
        Spacer(Modifier.height(8.dp))
        if (state.employees.isEmpty()) {
            EmptyMessage("No hay personal activo.")
            return@Column
        }
        val scroll = rememberScrollState()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(state.employees, key = { it.id }) { employee ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmployeeAvatar(employee.initial, employee.colorArgb, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(employee.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${state.plannedHours(employee.id).formatDecimalHours()} planificadas",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // Mismo scroll para todas las filas: en el móvil los días se desplazan a la vez.
                        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.days.forEach { day ->
                                DayCell(
                                    day = day,
                                    lines = state.shiftsFor(employee.id, day).map { "${it.start.formatTime()}–${it.end.formatTime()}" },
                                    onClick = if (viewModel.canEdit(employee.id)) {
                                        { editing = employee to day }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { (employee, day) ->
        ShiftDialog(
            employee = employee,
            day = day,
            shifts = state.shiftsFor(employee.id, day),
            onAdd = { start, end -> viewModel.addShift(employee.id, day, start, end) },
            onDelete = viewModel::deleteShift,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun DayCell(day: LocalDate, lines: List<String>, onClick: (() -> Unit)?) {
    // onClick == null: horario propio de una encargada, solo lectura (lo cambia la dueña).
    val modifier = Modifier.width(104.dp).heightIn(min = 72.dp)
    OutlinedCard(if (onClick != null) modifier.clickable(onClick = onClick) else modifier) {
        Column(Modifier.padding(8.dp)) {
            Text(day.formatShort(), style = MaterialTheme.typography.labelMedium)
            if (lines.isEmpty()) {
                Text("Libre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ShiftDialog(
    employee: Employee,
    day: LocalDate,
    shifts: List<com.salon.fichajes.domain.model.Shift>,
    onAdd: (String, String) -> Unit,
    onDelete: (com.salon.fichajes.domain.model.Shift) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf("10:00") }
    var end by remember { mutableStateOf("14:00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${employee.name} · ${day.formatShort()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shifts.forEach { shift ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${shift.start.formatTime()} – ${shift.end.formatTime()}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDelete(shift) }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar turno") }
                    }
                }
                if (shifts.isNotEmpty()) HorizontalDivider()
                Text("Añadir turno (puedes poner mañana y tarde por separado)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Entrada") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Salida") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(start, end) }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
