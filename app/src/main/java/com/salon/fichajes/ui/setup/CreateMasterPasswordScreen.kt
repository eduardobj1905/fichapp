package com.salon.fichajes.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.security.PasswordRules
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.ui.security.PasswordField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Para una dueña que viene de una versión anterior sin contraseña maestra (migración v2 → v3). */
@HiltViewModel
class CreateMasterPasswordViewModel @Inject constructor(
    private val repository: OwnerSecurityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun create(password: String, repeat: String) {
        PasswordRules.validate(password, repeat)?.let { _error.value = it; return }
        viewModelScope.launch {
            runCatching { repository.createMasterPassword(sessionManager.requireSession().employeeId, password) }
                .onSuccess { sessionManager.showRecoveryCode(it) }
                .onFailure { _error.value = it.message }
        }
    }

    fun logout() = sessionManager.logout()
}

@Composable
fun CreateMasterPasswordScreen(viewModel: CreateMasterPasswordViewModel = hiltViewModel()) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Crea tu contraseña maestra", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Eres la dueña del salón. Esta contraseña, que solo debes saber tú, protege las acciones delicadas: " +
                    "ascender o dar de baja a alguien, exportar las horas y ceder el control.",
            )
            PasswordField(password, { password = it }, "Contraseña (mín. ${PasswordRules.MIN_LENGTH}, letras y números)")
            PasswordField(repeat, { repeat = it }, "Repite la contraseña")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.create(password, repeat) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("Salir") }
        }
    }
}
