package com.salon.fichajes.ui.admin.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salon.fichajes.core.format.formatHours
import com.salon.fichajes.core.security.PasswordRules
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.ui.components.EmployeeAvatar
import com.salon.fichajes.ui.security.PasswordField
import com.salon.fichajes.ui.security.PinField

/**
 * Equipo (solo la dueña). Las acciones delicadas pasan por [runProtected], que pide la contraseña
 * maestra si no se ha escrito en los últimos minutos.
 */
@Composable
fun TeamScreen(
    snackbar: SnackbarHostState,
    runProtected: (() -> Unit) -> Unit,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialog.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Key, contentDescription = null) },
                        headlineContent = { Text("Contraseña maestra") },
                        supportingContent = { Text("Solo la sabes tú. Protege los cambios de rol, las bajas, la exportación y ceder el control.") },
                        trailingContent = {
                            TextButton(onClick = { viewModel.open(TeamDialog.ChangePassword) }) { Text("Cambiar") }
                        },
                    )
                }
            }
            items(employees, key = { it.id }) { employee ->
                EmployeeCard(
                    employee = employee,
                    isMe = employee.id == viewModel.currentUserId,
                    onAction = { dialog ->
                        // Ceder el control ya pide la contraseña actual dentro de su propio diálogo.
                        if (dialog is TeamDialog.Transfer) viewModel.open(dialog)
                        else runProtected { viewModel.open(dialog) }
                    },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { runProtected { viewModel.open(TeamDialog.Add) } },
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            text = { Text("Añadir persona") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    val error = dialogState.error
    val busy = dialogState.busy
    when (val dialog = dialogState.dialog) {
        null -> Unit
        TeamDialog.Add -> AddEmployeeDialog(error, busy, onDismiss = viewModel::close, onSave = viewModel::add)
        is TeamDialog.ChangeRole -> ChangeRoleDialog(dialog.employee, error, busy, viewModel::close) { viewModel.changeRole(dialog.employee, it) }
        is TeamDialog.ResetPin -> ResetPinDialog(dialog.employee, error, busy, viewModel::close) { p, r -> viewModel.resetPin(dialog.employee, p, r) }
        is TeamDialog.Deactivate -> AlertDialog(
            onDismissRequest = viewModel::close,
            title = { Text("Dar de baja a ${dialog.employee.name}") },
            text = {
                Column {
                    Text("Dejará de aparecer en la tablet. Sus fichajes se conservan 4 años, como exige la ley.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.deactivate(dialog.employee) }) { Text("Dar de baja") } },
            dismissButton = { TextButton(onClick = viewModel::close) { Text("Cancelar") } },
        )
        is TeamDialog.Transfer -> TransferDialog(
            employee = dialog.employee,
            verified = dialogState.transferVerified,
            error = error,
            busy = busy,
            onDismiss = viewModel::close,
            onVerify = viewModel::verifyTransferPassword,
        ) { current, new, repeat -> viewModel.transfer(dialog.employee, current, new, repeat) }
        TeamDialog.ChangePassword -> ChangePasswordDialog(error, busy, viewModel::close, viewModel::changePassword)
    }
}

@Composable
private fun EmployeeCard(employee: Employee, isMe: Boolean, onAction: (TeamDialog) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card {
        ListItem(
            leadingContent = { EmployeeAvatar(employee.initial, employee.colorArgb, size = 40.dp) },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(employee.name + if (isMe) " (tú)" else "")
                    AssistChip(onClick = {}, label = { Text(employee.role.label) })
                }
            },
            supportingContent = {
                Text("${employee.weeklyContractHours.formatHours()}/semana · ${employee.annualVacationDays} días de vacaciones")
            },
            trailingContent = {
                // La dueña no se gestiona a sí misma aquí: para dejar de serlo, cede el control.
                if (!isMe) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opciones") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf(
                                (if (employee.role == Role.MANAGER) "Quitar permisos de encargada" else "Ascender a encargada")
                                    to TeamDialog.ChangeRole(employee),
                                "Restablecer PIN" to TeamDialog.ResetPin(employee),
                                "Ceder el control (hacerla dueña)" to TeamDialog.Transfer(employee),
                                "Dar de baja" to TeamDialog.Deactivate(employee),
                            ).forEach { (text, dialog) ->
                                DropdownMenuItem(
                                    text = { Text(text) },
                                    onClick = {
                                        menuOpen = false
                                        onAction(dialog)
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DialogError(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun RoleSelector(selected: Role, onSelect: (Role) -> Unit) {
    Column {
        Permissions.ASSIGNABLE_ROLES.forEach { role ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = selected == role, onClick = { onSelect(role) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == role, onClick = { onSelect(role) })
                Column {
                    Text(role.label)
                    Text(
                        when (role) {
                            Role.MANAGER -> "Corrige fichajes, aprueba horas, hace horarios y registra bajas de las demás (no los suyos). Las vacaciones las aprueba la dueña."
                            else -> "Ficha y pide vacaciones."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEmployeeDialog(
    error: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, pin: String, hours: String, days: String, role: Role) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("40") }
    var days by remember { mutableStateOf("30") }
    var role by remember { mutableStateOf(Role.EMPLOYEE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva persona") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PinField(pin, { pin = it }, "PIN de 4 números (que lo escriba ella)")
                OutlinedTextField(
                    hours, { hours = it }, label = { Text("Horas de contrato por semana") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    days, { days = it }, label = { Text("Días de vacaciones al año") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                RoleSelector(role) { role = it }
                DialogError(error)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSave(name, pin, hours, days, role) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ChangeRoleDialog(employee: Employee, error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (Role) -> Unit) {
    var role by remember { mutableStateOf(employee.role) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permisos de ${employee.name}") },
        text = {
            Column {
                RoleSelector(role) { role = it }
                DialogError(error)
            }
        },
        confirmButton = { TextButton(enabled = !busy && role != employee.role, onClick = { onSave(role) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ResetPinDialog(employee: Employee, error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo PIN para ${employee.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pásale la tablet para que lo escriba ella.")
                PinField(pin, { pin = it }, "PIN nuevo")
                PinField(repeat, { repeat = it }, "Repite el PIN")
                DialogError(error)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSave(pin, repeat) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ChangePasswordDialog(error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña maestra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "Contraseña actual")
                PasswordField(new, { new = it }, "Nueva (mín. ${PasswordRules.MIN_LENGTH}, letras y números)")
                PasswordField(repeat, { repeat = it }, "Repite la nueva")
                DialogError(error)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSave(current, new, repeat) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Dos pasos: la dueña actual lo autoriza con su contraseña (se comprueba aquí, antes de pasar la tablet)
 * y la nueva dueña escribe la suya.
 */
@Composable
private fun TransferDialog(
    employee: Employee,
    verified: Boolean,
    error: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onVerify: (current: String) -> Unit,
    onConfirm: (current: String, new: String, repeat: String) -> Unit,
) {
    val step = if (verified) 2 else 1
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "Ceder el control a ${employee.name}" else "${employee.name}: crea tu contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 1) {
                    Text(
                        "${employee.name} pasará a ser la dueña (perfil maestro) y tú pasarás a encargada. " +
                            "Solo ella podrá devolverte el control."
                    )
                    PasswordField(current, { current = it }, "Tu contraseña maestra actual")
                } else {
                    Text("Pasa la tablet a ${employee.name}. Solo ella debe ver lo que escribe.", color = MaterialTheme.colorScheme.primary)
                    PasswordField(new, { new = it }, "Contraseña maestra nueva")
                    PasswordField(repeat, { repeat = it }, "Repítela")
                }
                DialogError(error)
            }
        },
        confirmButton = {
            if (step == 1) {
                TextButton(enabled = current.isNotBlank() && !busy, onClick = { onVerify(current) }) {
                    Text(if (busy) "Comprobando…" else "Siguiente")
                }
            } else {
                TextButton(enabled = !busy, onClick = { onConfirm(current, new, repeat) }) { Text("Ceder el control") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
