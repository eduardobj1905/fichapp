package com.salon.fichajes.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.security.PasswordRules
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MasterPasswordGateState(
    val visible: Boolean = false,
    val recoveryMode: Boolean = false,
    val checking: Boolean = false,
    val error: String? = null,
    val lockedSeconds: Int = 0,
)

/**
 * "sudo" de la dueña: antes de una acción delicada pide la contraseña maestra. Tras acertar,
 * no la vuelve a pedir durante InactivityPolicy.ELEVATION. Con 5 fallos seguidos se bloquea 60 s.
 */
@HiltViewModel
class MasterPasswordViewModel @Inject constructor(
    private val repository: OwnerSecurityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MasterPasswordGateState())
    val state: StateFlow<MasterPasswordGateState> = _state.asStateFlow()

    private var pendingAction: (() -> Unit)? = null
    private var failures = 0

    /** Ejecuta [action] si la dueña ya está verificada; si no, pide antes la contraseña. */
    fun runProtected(action: () -> Unit) {
        if (sessionManager.isElevated()) {
            action()
        } else {
            pendingAction = action
            _state.update { MasterPasswordGateState(visible = true, lockedSeconds = it.lockedSeconds) }
        }
    }

    fun dismiss() {
        pendingAction = null
        _state.update { it.copy(visible = false, error = null, recoveryMode = false) }
    }

    fun setRecoveryMode(enabled: Boolean) = _state.update { it.copy(recoveryMode = enabled, error = null) }

    fun submitPassword(password: String) {
        if (_state.value.checking || _state.value.lockedSeconds > 0) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            if (repository.verifyMasterPassword(password)) {
                failures = 0
                onUnlocked()
            } else {
                failures++
                if (failures >= MAX_FAILURES) {
                    failures = 0
                    lock()
                } else {
                    _state.update { it.copy(checking = false, error = "Contraseña incorrecta (${MAX_FAILURES - failures} intentos)") }
                }
            }
        }
    }

    fun submitRecovery(code: String, newPassword: String, repeat: String) {
        PasswordRules.validate(newPassword, repeat)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.resetWithRecoveryCode(sessionManager.requireSession().employeeId, code, newPassword)
            }.onSuccess { newCode ->
                sessionManager.showRecoveryCode(newCode)
                onUnlocked()
            }.onFailure { e ->
                _state.update { it.copy(checking = false, error = e.message) }
            }
        }
    }

    private fun onUnlocked() {
        sessionManager.elevate()
        _state.value = MasterPasswordGateState()
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    private suspend fun lock() {
        for (remaining in LOCK_SECONDS downTo 1) {
            _state.update { it.copy(checking = false, lockedSeconds = remaining, error = "Demasiados intentos") }
            delay(1_000)
        }
        _state.update { it.copy(lockedSeconds = 0, error = null) }
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val LOCK_SECONDS = 60
    }
}

@Composable
fun MasterPasswordDialog(viewModel: MasterPasswordViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return

    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val locked = state.lockedSeconds > 0

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(if (state.recoveryMode) "Recuperar contraseña maestra" else "Contraseña maestra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.recoveryMode) {
                    Text("Escribe el código de recuperación que apuntaste y elige una contraseña nueva.")
                    OutlinedTextField(code, { code = it }, label = { Text("Código (XXXX-XXXX-XXXX)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    PasswordField(newPassword, { newPassword = it }, "Nueva contraseña")
                    PasswordField(repeat, { repeat = it }, "Repite la contraseña")
                } else {
                    Text("Esta acción solo la puede hacer la dueña.")
                    PasswordField(password, { password = it }, "Contraseña")
                    TextButton(onClick = { viewModel.setRecoveryMode(true) }) { Text("¿La has olvidado?") }
                }
                val message = if (locked) "Demasiados intentos. Espera ${state.lockedSeconds} s" else state.error
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.checking && !locked,
                onClick = {
                    if (state.recoveryMode) viewModel.submitRecovery(code, newPassword, repeat)
                    else viewModel.submitPassword(password)
                },
            ) { Text(if (state.checking) "Comprobando…" else "Continuar") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (state.recoveryMode) viewModel.setRecoveryMode(false) else viewModel.dismiss()
            }) { Text(if (state.recoveryMode) "Volver" else "Cancelar") }
        },
    )
}
