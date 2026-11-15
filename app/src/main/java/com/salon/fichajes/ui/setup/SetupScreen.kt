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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.security.PasswordRules
import com.salon.fichajes.core.security.PinHasher
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.ui.components.SalonHeader
import com.salon.fichajes.ui.security.PasswordField
import com.salon.fichajes.ui.security.PinField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep {
    /** Lo rellena quien instala la app (p. ej. quien lleva la informática del salón). */
    SALON,
    /** Lo rellena SOLO la dueña: su PIN y su contraseña maestra. */
    OWNER_SECRETS,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.SALON,
    val ownerName: String = "",
    val weeklyHours: String = "40",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * Primer arranque en dos pasos para "ceder el control": quien instala configura lo básico y
 * pasa la tablet a la dueña, que escribe su PIN y su contraseña maestra sin que nadie más los vea.
 * No hay ningún PIN ni contraseña por defecto en el código.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val securityRepository: OwnerSecurityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun continueToOwner(name: String, weeklyHours: String) {
        val error = when {
            name.isBlank() -> "Escribe el nombre de la dueña"
            weeklyHours.replace(',', '.').toDoubleOrNull() == null -> "Horas semanales no válidas"
            else -> null
        }
        _state.update {
            if (error != null) it.copy(error = error)
            else it.copy(step = SetupStep.OWNER_SECRETS, ownerName = name.trim(), weeklyHours = weeklyHours, error = null)
        }
    }

    fun back() = _state.update { it.copy(step = SetupStep.SALON, error = null) }

    fun finish(pin: String, pinRepeat: String, password: String, passwordRepeat: String) {
        val error = when {
            !PinHasher.isValidPin(pin) -> "El PIN debe tener exactamente 4 números"
            pin != pinRepeat -> "Los PIN no coinciden"
            else -> PasswordRules.validate(password, passwordRepeat)
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val current = _state.value
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                securityRepository.setupOwner(
                    name = current.ownerName,
                    weeklyContractHours = current.weeklyHours.replace(',', '.').toDouble(),
                    pin = pin,
                    password = password,
                )
            }.onSuccess { code ->
                // RootViewModel pasa al login y el código se enseña encima, una sola vez.
                sessionManager.showRecoveryCode(code)
            }.onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}

@Composable
fun SetupScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                SetupStep.SALON -> SalonStep(state, onNext = viewModel::continueToOwner)
                SetupStep.OWNER_SECRETS -> OwnerStep(state, onBack = viewModel::back, onFinish = viewModel::finish)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SalonStep(state: SetupUiState, onNext: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.ownerName) }
    var hours by rememberSaveable { mutableStateOf(state.weeklyHours) }
    SalonHeader()
    Text("Configurar los fichajes", style = MaterialTheme.typography.headlineMedium)
    Text("Paso 1 de 2 · Datos de la dueña del salón", style = MaterialTheme.typography.titleMedium)
    Text(
        "La dueña es el perfil maestro: podrá dar de alta al equipo, nombrar encargadas y corregir horas.",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(name, { name = it }, label = { Text("Nombre de la dueña") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        hours, { hours = it }, label = { Text("Sus horas de contrato a la semana") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onNext(name, hours) }, modifier = Modifier.fillMaxWidth()) { Text("Siguiente") }
}

@Composable
private fun OwnerStep(state: SetupUiState, onBack: () -> Unit, onFinish: (String, String, String, String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var pinRepeat by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordRepeat by rememberSaveable { mutableStateOf("") }
    Text("Paso 2 de 2 · Solo para ${state.ownerName}", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Si estás configurando la tablet para otra persona, pásasela ahora. " +
            "Nadie más debe ver lo que escribe aquí.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text("PIN de 4 números: para entrar cada día y fichar.", style = MaterialTheme.typography.bodyMedium)
    PinField(pin, { pin = it }, "PIN")
    PinField(pinRepeat, { pinRepeat = it }, "Repite el PIN")
    Text(
        "Contraseña maestra (mínimo ${PasswordRules.MIN_LENGTH} caracteres, con letras y números): " +
            "se pedirá para ascender o dar de baja a alguien, exportar las horas o ceder el control.",
        style = MaterialTheme.typography.bodyMedium,
    )
    PasswordField(password, { password = it }, "Contraseña maestra")
    PasswordField(passwordRepeat, { passwordRepeat = it }, "Repite la contraseña")
    Button(
        onClick = { onFinish(pin, pinRepeat, password, passwordRepeat) },
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (state.isSaving) "Guardando…" else "Terminar configuración") }
    OutlinedButton(onClick = onBack, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Atrás") }
}
