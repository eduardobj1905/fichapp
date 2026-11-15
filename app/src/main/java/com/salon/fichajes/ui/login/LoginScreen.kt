package com.salon.fichajes.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.components.SalonHeader

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        val selected = state.selected
        when {
            state.isLoading -> LoadingBox()
            selected == null -> EmployeeGrid(state.employees, onSelect = viewModel::select)
            else -> PinPad(
                employee = selected,
                entry = state.pinEntry,
                onDigit = viewModel::onDigit,
                onDelete = viewModel::onDelete,
                onCancel = viewModel::cancel,
            )
        }
    }
}

@Composable
private fun EmployeeGrid(employees: List<Employee>, onSelect: (Employee) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        SalonHeader()
        Spacer(Modifier.height(24.dp))
        Text("¿Quién eres?", style = MaterialTheme.typography.headlineLarge)
        Text("Toca tu nombre para fichar", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(employees, key = { it.id }) { employee ->
                Card(onClick = { onSelect(employee) }) {
                    Column(
                        Modifier.padding(20.dp).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmployeeAvatar(employee.initial, employee.colorArgb, size = 72.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(employee.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        if (employee.role != Role.EMPLOYEE) {
                            Text(employee.role.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPad(
    employee: Employee,
    entry: PinEntryState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmployeeAvatar(employee.initial, employee.colorArgb, size = 80.dp)
        Spacer(Modifier.height(8.dp))
        Text("Hola, ${employee.name}", style = MaterialTheme.typography.headlineSmall)
        Text("Introduce tu PIN", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                val filled = index < entry.pin.length
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val message = when {
            entry.lockedSeconds > 0 -> "Demasiados intentos. Espera ${entry.lockedSeconds} s"
            entry.isChecking -> "Comprobando…"
            else -> entry.error.orEmpty()
        }
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.height(24.dp))
        Spacer(Modifier.height(12.dp))

        val enabled = !entry.isChecking && entry.lockedSeconds == 0
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit -> PinKey(digit.toString(), enabled) { onDigit(digit) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, modifier = Modifier.size(84.dp)) { Text("Salir") }
            PinKey("0", enabled) { onDigit('0') }
            FilledTonalButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(84.dp), shape = CircleShape) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Borrar")
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(84.dp),
        shape = CircleShape,
    ) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}
