package com.salon.fichajes.ui.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.domain.repository.OwnerSetupState
import com.salon.fichajes.session.InactivityPolicy
import com.salon.fichajes.session.Session
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.ui.admin.entries.EmployeeEntriesScreen
import com.salon.fichajes.ui.admin.home.AdminHomeScreen
import com.salon.fichajes.ui.components.LoadingBox
import com.salon.fichajes.ui.employee.clock.ClockScreen
import com.salon.fichajes.ui.employee.vacations.EmployeeVacationsScreen
import com.salon.fichajes.ui.login.LoginScreen
import com.salon.fichajes.ui.security.RecoveryCodeDialog
import com.salon.fichajes.ui.setup.CreateMasterPasswordScreen
import com.salon.fichajes.ui.setup.SetupScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

object Routes {
    const val CLOCK = "employee/clock"
    const val EMPLOYEE_VACATIONS = "employee/vacations"
    const val MANAGEMENT = "management"
    const val MANAGEMENT_ENTRIES = "management/entries/{employeeId}"
    fun managementEntries(employeeId: Long) = "management/entries/$employeeId"
}

sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data object LoggedOut : RootState
    /** La dueña viene de una versión sin contraseña maestra: debe crearla antes de seguir. */
    data object NeedsMasterPassword : RootState
    data class LoggedIn(val session: Session) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    securityRepository: OwnerSecurityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    val state: StateFlow<RootState> =
        combine(securityRepository.observeSetupState(), sessionManager.session) { setup, session ->
            when {
                setup == OwnerSetupState.NO_OWNER -> RootState.NeedsSetup
                session == null -> RootState.LoggedOut
                session.role == Role.OWNER && setup == OwnerSetupState.NO_MASTER_PASSWORD -> RootState.NeedsMasterPassword
                else -> RootState.LoggedIn(session)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.Loading)

    val recoveryCode: StateFlow<String?> = sessionManager.recoveryCodeToShow

    /** Segundos que faltan para cerrar sesión por inactividad; null si no hay que avisar todavía. */
    private val _inactivityWarning = MutableStateFlow<Long?>(null)
    val inactivityWarning: StateFlow<Long?> = _inactivityWarning.asStateFlow()

    init {
        // Vigilante de inactividad: una comprobación por segundo es despreciable en batería.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val session = sessionManager.session.value
                // Mientras se enseña el código de recuperación no se cierra la sesión.
                if (session == null || sessionManager.recoveryCodeToShow.value != null) {
                    _inactivityWarning.value = null
                    continue
                }
                val left = InactivityPolicy.secondsLeft(
                    lastInteractionMs = sessionManager.lastInteractionMs,
                    nowMs = sessionManager.nowMs(),
                    timeout = InactivityPolicy.timeoutFor(session.role),
                )
                _inactivityWarning.value = when {
                    left <= 0 -> {
                        sessionManager.logout()
                        null
                    }
                    left <= InactivityPolicy.WARNING.inWholeSeconds -> left
                    else -> null
                }
            }
        }
    }

    fun stayLoggedIn() {
        sessionManager.registerInteraction()
        _inactivityWarning.value = null
    }

    fun logout() = sessionManager.logout()

    fun recoveryCodeWritten() = sessionManager.dismissRecoveryCode()
}

/**
 * La raíz decide por ESTADO (no por back stack) qué se ve: sin dueña → configuración inicial;
 * sin sesión → PIN; con sesión → el NavHost. Así, al cerrar sesión no queda ninguna
 * pantalla de otra persona en la pila (importante en una tablet compartida).
 */
@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val warning by viewModel.inactivityWarning.collectAsStateWithLifecycle()
    val recoveryCode by viewModel.recoveryCode.collectAsStateWithLifecycle()

    when (val s = state) {
        RootState.Loading -> LoadingBox()
        RootState.NeedsSetup -> SetupScreen()
        RootState.LoggedOut -> LoginScreen()
        RootState.NeedsMasterPassword -> CreateMasterPasswordScreen()
        is RootState.LoggedIn -> key(s.session) { SessionNavHost(s.session) }
    }

    if (state is RootState.LoggedIn || state is RootState.NeedsMasterPassword) {
        warning?.let { seconds ->
            AlertDialog(
                onDismissRequest = viewModel::stayLoggedIn,
                title = { Text("¿Sigues ahí?") },
                text = { Text("Por seguridad, la sesión se cerrará en $seconds s.") },
                confirmButton = { TextButton(onClick = viewModel::stayLoggedIn) { Text("Seguir") } },
                dismissButton = { TextButton(onClick = viewModel::logout) { Text("Cerrar sesión") } },
            )
        }
    }

    recoveryCode?.let { RecoveryCodeDialog(code = it, onDone = viewModel::recoveryCodeWritten) }
}

@Composable
private fun SessionNavHost(session: Session) {
    val navController = rememberNavController()
    // Todo el mundo empieza en su pantalla de fichar (la dueña y las encargadas también fichan).
    NavHost(navController = navController, startDestination = Routes.CLOCK) {
        composable(Routes.CLOCK) {
            ClockScreen(
                onOpenVacations = { navController.navigate(Routes.EMPLOYEE_VACATIONS) },
                onOpenManagement = if (session.role.canManage) {
                    { navController.navigate(Routes.MANAGEMENT) }
                } else null,
            )
        }
        composable(Routes.EMPLOYEE_VACATIONS) {
            EmployeeVacationsScreen(onBack = { navController.popBackStack() })
        }
        if (session.role.canManage) {
            composable(Routes.MANAGEMENT) {
                AdminHomeScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEmployeeEntries = { id -> navController.navigate(Routes.managementEntries(id)) },
                )
            }
            composable(
                route = Routes.MANAGEMENT_ENTRIES,
                arguments = listOf(navArgument("employeeId") { type = NavType.LongType }),
            ) {
                EmployeeEntriesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
