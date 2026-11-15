package com.salon.fichajes.ui.admin.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatDecimalHours
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.AbsenceType
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.EmptyMessage
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.PeriodNavigator
import com.salon.fichajes.ui.theme.ClockOutRed
import java.time.LocalDate

/** Estado de una celda del horario (un día de una persona). */
private sealed interface DayStatus {
    data class Working(val shifts: List<Shift>) : DayStatus
    data class OnLeave(val absence: Absence) : DayStatus
    data object OnVacation : DayStatus
}

/**
 * Vista semanal: una fila por persona y una celda por día. Toca una celda para editar turnos
 * (con su descanso) o para poner / quitar una baja desde ese día.
 */
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
        Text(
            "Toca un día para poner turnos con su descanso, o para registrar una baja.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                val status = state.absenceOn(employee.id, day)?.let { DayStatus.OnLeave(it) }
                                    ?: if (state.onVacation(employee.id, day)) DayStatus.OnVacation
                                    else DayStatus.Working(state.shiftsFor(employee.id, day))
                                DayCell(
                                    day = day,
                                    status = status,
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
        DayDialog(
            employee = employee,
            day = day,
            shifts = state.shiftsFor(employee.id, day),
            absence = state.absenceOn(employee.id, day),
            onVacation = state.onVacation(employee.id, day),
            onAddShift = { s, e, bs, be -> viewModel.addShift(employee.id, day, s, e, bs, be) },
            onDeleteShift = viewModel::deleteShift,
            onStartAbsence = { type, note ->
                viewModel.startAbsence(employee, day, type, note)
                editing = null
            },
            onEndAbsence = { absence ->
                viewModel.endAbsence(absence, day)
                editing = null
            },
            onDeleteAbsence = { absence ->
                viewModel.deleteAbsence(absence)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun Shift.label(): String =
    "${start.formatTime()}–${end.formatTime()}" +
        if (hasBreak) "\ndesc. ${breakStart!!.formatTime()}–${breakEnd!!.formatTime()}" else ""

@Composable
private fun DayCell(day: LocalDate, status: DayStatus, onClick: (() -> Unit)?) {
    // onClick == null: horario propio de una encargada, solo lectura (lo cambia la dueña).
    val modifier = Modifier.width(112.dp).heightIn(min = 80.dp)
    val colors = when (status) {
        is DayStatus.OnLeave -> CardDefaults.outlinedCardColors(containerColor = ClockOutRed.copy(alpha = 0.15f))
        DayStatus.OnVacation -> CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        is DayStatus.Working -> CardDefaults.outlinedCardColors()
    }
    OutlinedCard(if (onClick != null) modifier.clickable(onClick = onClick) else modifier, colors = colors) {
        Column(Modifier.padding(8.dp)) {
            Text(day.formatShort(), style = MaterialTheme.typography.labelMedium)
            when (status) {
                is DayStatus.OnLeave -> Text(status.absence.type.label, style = MaterialTheme.typography.bodySmall, color = ClockOutRed, fontWeight = FontWeight.SemiBold)
                DayStatus.OnVacation -> Text("Vacaciones", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                is DayStatus.Working ->
                    if (status.shifts.isEmpty()) {
                        Text("Libre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        status.shifts.forEach { Text(it.label(), style = MaterialTheme.typography.bodySmall) }
                    }
            }
        }
    }
}

@Composable
private fun DayDialog(
    employee: Employee,
    day: LocalDate,
    shifts: List<Shift>,
    absence: Absence?,
    onVacation: Boolean,
    onAddShift: (start: String, end: String, breakStart: String, breakEnd: String) -> Unit,
    onDeleteShift: (Shift) -> Unit,
    onStartAbsence: (AbsenceType, String) -> Unit,
    onEndAbsence: (Absence) -> Unit,
    onDeleteAbsence: (Absence) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf("10:00") }
    var end by remember { mutableStateOf("19:00") }
    var breakStart by remember { mutableStateOf("14:00") }
    var breakEnd by remember { mutableStateOf("15:00") }
    var showAbsenceForm by remember { mutableStateOf(false) }
    var absenceType by remember { mutableStateOf(AbsenceType.SICK_LEAVE) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${employee.name} · ${day.formatShort()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // --- Baja ---
                if (absence != null) {
                    Text(
                        "${absence.type.label} desde el ${absence.startDate.formatShort()}" +
                            (absence.endDate?.let { " hasta el ${it.formatShort()}" } ?: " (sin fecha de alta)"),
                        color = ClockOutRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                    absence.note?.let { Text("Nota: $it", style = MaterialTheme.typography.bodySmall) }
                    if (day.isAfter(absence.startDate)) {
                        OutlinedButton(onClick = { onEndAbsence(absence) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Se reincorpora este día (dar el alta)")
                        }
                    }
                    TextButton(onClick = { onDeleteAbsence(absence) }) { Text("Eliminar la baja (se puso por error)") }
                    return@Column
                }
                if (onVacation) {
                    Text("Está de vacaciones aprobadas este día.", style = MaterialTheme.typography.bodyMedium)
                }

                // --- Turnos ---
                shifts.forEach { shift ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shift.label().replace("\n", " · "), modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteShift(shift) }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar turno") }
                    }
                }
                if (shifts.isNotEmpty()) HorizontalDivider()
                Text("Añadir turno", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Entrada") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Salida") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Descanso previsto (déjalo en blanco si no hay)", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(breakStart, { breakStart = it }, label = { Text("Desde") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(breakEnd, { breakEnd = it }, label = { Text("Hasta") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { onAddShift(start, end, breakStart, breakEnd) }) { Text("Añadir turno") }

                HorizontalDivider()
                if (!showAbsenceForm) {
                    TextButton(onClick = { showAbsenceForm = true }) { Text("Poner de baja desde este día…", color = ClockOutRed) }
                } else {
                    Text("Baja desde el ${day.formatShort()} (sin fecha de alta; se la das cuando vuelva)", style = MaterialTheme.typography.titleSmall)
                    AbsenceType.entries.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = absenceType == type, onClick = { absenceType = type }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = absenceType == type, onClick = { absenceType = type })
                            Text(type.label)
                        }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { onStartAbsence(absenceType, note) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Registrar baja")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
