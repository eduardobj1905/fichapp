package com.salon.fichajes.ui.employee.clock

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatHoursMinutes
import com.salon.fichajes.core.format.formatShortDate
import com.salon.fichajes.core.format.formatStopwatch
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.ui.components.CorrectionNote
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.EmptyMessage
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.StatCard
import com.salon.fichajes.ui.theme.ClockInGreen
import com.salon.fichajes.ui.theme.ClockOutRed
import java.time.Duration
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    onOpenVacations: () -> Unit,
    /** Solo para la dueña y las encargadas; null para una empleada. */
    onOpenManagement: (() -> Unit)?,
    viewModel: ClockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // Eventos de un solo uso (snackbar, háptica) vía Channel: no se repiten al rotar la pantalla.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ClockEvent.ClockedSuccessfully -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                is ClockEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val compact = LocalConfiguration.current.screenWidthDp < 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.employee?.let { EmployeeAvatar(it.initial, it.colorArgb, size = 36.dp) }
                        Spacer(Modifier.width(12.dp))
                        Text(state.employee?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    val openVacations = {
                        viewModel.cancelAutoLogout()
                        onOpenVacations()
                    }
                    // En móvil, con el botón Gestión no cabe todo: vacaciones solo con icono.
                    if (compact && onOpenManagement != null) {
                        IconButton(onClick = openVacations) {
                            Icon(Icons.Filled.BeachAccess, contentDescription = "Mis vacaciones")
                        }
                    } else {
                        TextButton(onClick = openVacations) {
                            Icon(Icons.Filled.BeachAccess, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mis vacaciones")
                        }
                    }
                    if (onOpenManagement != null) {
                        FilledTonalButton(onClick = {
                            viewModel.cancelAutoLogout()
                            onOpenManagement()
                        }) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gestión")
                        }
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Tablet en horizontal: botón a la izquierda, resumen e historial a la derecha.
            if (maxWidth > 700.dp) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ClockButtonSection(state, onClick = viewModel::onClockButton)
                    }
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CorrectionsBanner(state, onAcknowledge = viewModel::acknowledgeCorrections)
                        SummaryCards(state)
                        RecentEntries(state.recent, state.zone, state.staffNames, Modifier.weight(1f))
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CorrectionsBanner(state, onAcknowledge = viewModel::acknowledgeCorrections)
                    ClockButtonSection(state, onClick = viewModel::onClockButton)
                    SummaryCards(state)
                    RecentEntries(state.recent, state.zone, state.staffNames, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ClockButtonSection(state: ClockUiState, onClick: () -> Unit) {
    val working = state.isWorking
    val color by animateColorAsState(if (working) ClockOutRed else ClockInGreen, label = "clockButtonColor")
    val label = if (working) "FICHAR\nSALIDA" else "FICHAR\nENTRADA"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = !state.isProcessing,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White,
                disabledContainerColor = color.copy(alpha = 0.5f),
                disabledContentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .size(240.dp)
                .semantics { contentDescription = label.replace('\n', ' ') },
        ) {
            Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 36.sp)
        }
        Spacer(Modifier.height(16.dp))
        val open = state.openEntry
        if (open != null) {
            Text("Trabajando desde las ${open.clockIn.formatTime(state.zone)}", style = MaterialTheme.typography.titleMedium)
            Text(
                state.currentElapsed.formatStopwatch(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
            )
        } else {
            Text("Ahora mismo no estás fichada", style = MaterialTheme.typography.titleMedium)
            Text("--:--:--", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryCards(state: ClockUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Hoy", state.today.formatHoursMinutes(), subtitle = null, modifier = Modifier.weight(1f))
            StatCard(
                "Esta semana",
                state.week.formatHoursMinutes(),
                subtitle = "de ${state.weekTarget.formatHoursMinutes()} de contrato",
                modifier = Modifier.weight(1f),
            )
        }
        LinearProgressIndicator(progress = { state.weekProgress }, modifier = Modifier.fillMaxWidth().height(8.dp))
    }
}

/** Aviso visible hasta que la empleada pulsa "Entendido": la jefa ha tocado sus fichajes. */
@Composable
private fun CorrectionsBanner(state: ClockUiState, onAcknowledge: () -> Unit) {
    if (state.unseenCorrections.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.unseenCorrections.size == 1) "Se ha modificado 1 de tus fichajes"
                    else "Se han modificado ${state.unseenCorrections.size} de tus fichajes",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            state.unseenCorrections.forEach { entry ->
                Text(
                    "${entry.clockIn.formatShortDate(state.zone)}: ${entry.clockIn.formatTime(state.zone)} – " +
                        (entry.clockOut?.formatTime(state.zone) ?: "en curso"),
                    fontWeight = FontWeight.SemiBold,
                )
                CorrectionNote(entry, state.zone, adminName = entry.correctedBy?.let(state.staffNames::get))
            }
            TextButton(onClick = onAcknowledge, modifier = Modifier.align(Alignment.End)) { Text("Entendido") }
        }
    }
}

@Composable
private fun RecentEntries(entries: List<ClockEntry>, zone: ZoneId, staffNames: Map<Long, String>, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Text("Fichajes recientes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        if (entries.isEmpty()) {
            EmptyMessage("Todavía no hay fichajes.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    RecentEntryRow(entry, zone, adminName = entry.correctedBy?.let(staffNames::get))
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RecentEntryRow(entry: ClockEntry, zone: ZoneId, adminName: String?) {
    val out = entry.clockOut
    ListItem(
        leadingContent = {
            Icon(
                if (entry.isAdminModified) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Login,
                contentDescription = if (entry.isAdminModified) "Modificado por la encargada" else null,
            )
        },
        headlineContent = {
            Text(
                "${entry.clockIn.formatTime(zone)} – ${out?.formatTime(zone) ?: "en curso"}",
                fontWeight = if (out == null) FontWeight.Bold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Column {
                Text(entry.clockIn.formatShortDate(zone))
                CorrectionNote(entry, zone, adminName)
            }
        },
        trailingContent = { if (out != null) Text(Duration.between(entry.clockIn, out).formatHoursMinutes()) },
    )
}
