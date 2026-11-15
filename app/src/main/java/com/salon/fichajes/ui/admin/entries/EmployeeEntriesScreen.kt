package com.salon.fichajes.ui.admin.entries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatDecimalHours
import com.salon.fichajes.core.format.formatHoursMinutes
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.ui.components.CorrectionNote
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.PeriodNavigator
import java.time.Duration
import java.time.ZoneId

/** La jefa revisa la semana de una empleada y puede corregir o añadir fichajes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeEntriesScreen(
    onBack: () -> Unit,
    viewModel: EmployeeEntriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.employee?.let { EmployeeAvatar(it.initial, it.colorArgb, size = 36.dp) }
                        Spacer(Modifier.width(12.dp))
                        Text("Fichajes de ${state.employee?.name.orEmpty()}")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PeriodNavigator(
                    label = "${state.days.first().date.formatShort()} – ${state.days.last().date.formatShort()}",
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                )
                Text(
                    "Total semana: ${state.weekTotal.formatHoursMinutes()} de ${state.weekTarget.formatDecimalHours()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (viewModel.canEdit) {
                        "Toca el lápiz para corregir un fichaje o + para añadir uno olvidado. " +
                            "La empleada verá un aviso con el cambio y el motivo."
                    } else {
                        "Son tus propios fichajes: solo puedes verlos. Si hay que corregir algo, pídeselo a la dueña."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                DayCard(
                    day = day,
                    zone = state.zone,
                    adminNames = state.adminNames,
                    canEdit = viewModel.canEdit,
                    canAdd = viewModel.canEdit && !day.date.isAfter(state.today),
                    onAdd = { viewModel.startAdd(day.date) },
                    onEdit = viewModel::startEdit,
                )
            }
        }
    }

    state.editor?.let { editor ->
        EntryEditorDialog(
            editor = editor,
            zone = state.zone,
            error = state.editorError,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::save,
        )
    }
}

@Composable
private fun DayCard(
    day: DayEntries,
    zone: ZoneId,
    adminNames: Map<Long, String>,
    canEdit: Boolean,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onEdit: (ClockEntry) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.date.formatShort(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(day.worked.formatHoursMinutes(), fontWeight = FontWeight.SemiBold)
                if (canAdd) {
                    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Añadir fichaje") }
                }
            }
            if (day.entries.isEmpty()) {
                Text("Sin fichajes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val out = entry.clockOut
                        Text(
                            "${entry.clockIn.formatTime(zone)} – ${out?.formatTime(zone) ?: "en curso"}" +
                                (out?.let { "  ·  " + Duration.between(entry.clockIn, it).formatHoursMinutes() } ?: "") +
                                (if (entry.endedForBreak) "  ·  salida a descanso" else ""),
                        )
                        CorrectionNote(entry, zone, adminName = entry.correctedBy?.let(adminNames::get))
                    }
                    if (canEdit) {
                        IconButton(onClick = { onEdit(entry) }) { Icon(Icons.Filled.Edit, contentDescription = "Corregir fichaje") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryEditorDialog(
    editor: EntryEditor,
    zone: ZoneId,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (inText: String, outText: String, reason: String) -> Unit,
) {
    val existing = (editor as? EntryEditor.Edit)?.entry
    var inText by rememberSaveable(editor) { mutableStateOf(existing?.clockIn?.formatTime(zone).orEmpty()) }
    var outText by rememberSaveable(editor) { mutableStateOf(existing?.clockOut?.formatTime(zone).orEmpty()) }
    var reason by rememberSaveable(editor) {
        mutableStateOf(if (existing == null) "Olvidó fichar" else existing.correctionReason.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Añadir fichaje · ${editor.date.formatShort()}" else "Corregir fichaje · ${editor.date.formatShort()}") },
        text = {
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(inText, { inText = it }, label = { Text("Entrada (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(outText, { outText = it }, label = { Text("Salida (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (existing?.isOpen == true) {
                    Text("Deja la salida vacía si sigue trabajando.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Si la salida es antes que la entrada, se entiende que es del día siguiente.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo (lo verá la empleada)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(inText, outText, reason) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
