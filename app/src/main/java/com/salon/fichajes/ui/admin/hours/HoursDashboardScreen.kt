package com.salon.fichajes.ui.admin.hours

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.OutlinedButton
import com.salon.fichajes.ui.components.PeriodNavigator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatDateTime
import com.salon.fichajes.core.format.formatDecimalHours
import com.salon.fichajes.core.format.formatHoursMinutes
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.EmptyMessage
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.StatCard
import com.salon.fichajes.ui.theme.ClockInGreen
import com.salon.fichajes.ui.theme.WarningAmber
import java.time.ZoneId

@Composable
fun HoursDashboardScreen(
    snackbar: SnackbarHostState,
    onOpenEmployee: (employeeId: Long) -> Unit,
    /** Exportar es solo de la dueña y pide la contraseña maestra. */
    canExport: Boolean,
    runProtected: (() -> Unit) -> Unit,
    viewModel: HoursDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var correcting by remember { mutableStateOf<ClockEntry?>(null) }

    // Selector de archivo del sistema: la jefa elige dónde guardar (Descargas, pendrive, Drive…).
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    if (state.isLoading) {
        LoadingBox()
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HoursPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            shape = SegmentedButtonDefaults.itemShape(index, HoursPeriod.entries.size),
                        ) { Text(period.label) }
                    }
                }
                PeriodNavigator(state.periodLabel, viewModel::previousPeriod, viewModel::nextPeriod)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Toca a una persona para ver o corregir sus fichajes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (canExport) {
                        OutlinedButton(
                            onClick = { runProtected { exportLauncher.launch(state.exportFileName) } },
                            enabled = !state.isExporting,
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isExporting) "Exportando…" else "Exportar hoja (CSV)")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total del equipo", state.teamTotal.formatDecimalHours(), null, Modifier.weight(1f))
                    if (state.isCurrentPeriod) {
                        StatCard("En el salón ahora", "${state.workingNow}", "de ${state.rows.size} personas", Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.staleOpenEntries.isNotEmpty()) {
            items(state.staleOpenEntries, key = { "stale-${it.first.id}" }, span = { GridItemSpan(maxLineSpan) }) { (entry, employee) ->
                StaleEntryCard(entry, employee, state.zone, onCorrect = { correcting = entry })
            }
        }

        if (state.rows.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyMessage("Da de alta a tu equipo en la pestaña Equipo.") }
        }
        items(state.rows, key = { it.employee.id }) { row ->
            EmployeeHoursCard(row, state.zone, onClick = { onOpenEmployee(row.employee.id) })
        }
    }

    correcting?.let { entry ->
        CloseEntryDialog(
            entry = entry,
            zone = state.zone,
            onDismiss = { correcting = null },
            onConfirm = { time, reason ->
                correcting = null
                viewModel.closeStaleEntry(entry, time, reason)
            },
        )
    }
}

@Composable
private fun EmployeeHoursCard(row: EmployeeHoursRow, zone: ZoneId, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmployeeAvatar(row.employee.initial, row.employee.colorArgb, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.employee.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${row.worked.formatHoursMinutes()} de ${row.target.formatDecimalHours()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                row.workingSince?.let {
                    Badge(containerColor = ClockInGreen, contentColor = Color.White) {
                        Text("En el salón desde ${it.formatTime(zone)}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { row.progress },
                color = Color(row.employee.colorArgb),
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
private fun StaleEntryCard(entry: ClockEntry, employee: Employee?, zone: ZoneId, onCorrect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.2f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${employee?.name ?: "Empleada"} no fichó la salida", fontWeight = FontWeight.SemiBold)
                Text("Entrada abierta desde el ${entry.clockIn.formatDateTime(zone)}")
            }
            TextButton(onClick = onCorrect) { Text("Corregir") }
        }
    }
}

@Composable
private fun CloseEntryDialog(
    entry: ClockEntry,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (time: String, reason: String) -> Unit,
) {
    var time by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Olvidó fichar la salida") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar tramo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Entrada: ${entry.clockIn.formatDateTime(zone)}. ¿A qué hora salió ese día?")
                OutlinedTextField(time, { time = it }, label = { Text("Hora de salida (HH:mm)") }, singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo (queda registrado)") })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(time, reason) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
