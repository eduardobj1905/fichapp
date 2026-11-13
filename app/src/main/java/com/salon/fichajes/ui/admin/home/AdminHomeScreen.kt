package com.salon.fichajes.ui.admin.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.ui.admin.hours.HoursDashboardScreen
import com.salon.fichajes.ui.admin.schedule.ScheduleScreen
import com.salon.fichajes.ui.admin.team.TeamScreen
import com.salon.fichajes.ui.admin.vacations.AdminVacationsScreen
import com.salon.fichajes.ui.security.MasterPasswordDialog
import com.salon.fichajes.ui.security.MasterPasswordViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private enum class AdminTab(val label: String, val icon: ImageVector, val ownerOnly: Boolean = false) {
    HOURS("Horas", Icons.Filled.Schedule),
    SCHEDULE("Horario", Icons.Filled.CalendarMonth),
    VACATIONS("Vacaciones", Icons.Filled.BeachAccess),
    TEAM("Equipo", Icons.Filled.Groups, ownerOnly = true),
}

@HiltViewModel
class AdminHomeViewModel @Inject constructor(private val sessionManager: SessionManager) : ViewModel() {
    val session = sessionManager.requireSession()
    val isOwner: Boolean get() = Permissions.canAdministerTeam(session.role)
    fun logout() = sessionManager.logout()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    onBack: () -> Unit,
    onOpenEmployeeEntries: (employeeId: Long) -> Unit,
    viewModel: AdminHomeViewModel = hiltViewModel(),
    passwordGate: MasterPasswordViewModel = hiltViewModel(),
) {
    // La encargada no ve la pestaña Equipo: no puede dar de alta, ascender ni dar de baja.
    val tabs = AdminTab.entries.filter { !it.ownerOnly || viewModel.isOwner }
    var tab by rememberSaveable { mutableStateOf(AdminTab.HOURS) }
    val snackbar = remember { SnackbarHostState() }
    val runProtected: (() -> Unit) -> Unit = passwordGate::runProtected

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a fichar") }
                    },
                    title = { Text("Gestión · ${viewModel.session.role.label} · ${tab.label}") },
                    actions = {
                        IconButton(onClick = viewModel::logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                        }
                    },
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                // En tablet, navegación lateral (NavigationRail) para aprovechar el ancho.
                if (wide) {
                    NavigationRail {
                        tabs.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (tab) {
                        AdminTab.HOURS -> HoursDashboardScreen(
                            snackbar = snackbar,
                            onOpenEmployee = onOpenEmployeeEntries,
                            canExport = viewModel.isOwner,
                            runProtected = runProtected,
                        )
                        AdminTab.SCHEDULE -> ScheduleScreen(snackbar)
                        AdminTab.VACATIONS -> AdminVacationsScreen(snackbar)
                        AdminTab.TEAM -> if (viewModel.isOwner) TeamScreen(snackbar, runProtected)
                    }
                }
            }
        }
    }

    MasterPasswordDialog(passwordGate)
}
