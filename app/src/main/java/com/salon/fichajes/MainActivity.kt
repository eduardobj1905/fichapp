package com.salon.fichajes

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.ui.navigation.AppRoot
import com.salon.fichajes.ui.theme.FichappTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tablet de quiosco: la pantalla no se apaga mientras la app está delante.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            FichappTheme {
                AppRoot()
            }
        }
    }

    /** Android lo llama en cada toque o tecla: reinicia el contador de inactividad. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionManager.registerInteraction()
    }

    /**
     * La usuaria sale de la app con el botón Inicio o cambiando de app: cerramos la sesión para que,
     * al volver, nadie encuentre el panel de otra persona abierto. No salta al abrir el selector de
     * archivos de la exportación, porque esa pantalla la lanza la propia app.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        sessionManager.logout()
    }

    /** Salir con el botón Atrás destruye la Activity, pero el proceso (y la sesión) seguiría vivo. */
    override fun onDestroy() {
        if (isFinishing) sessionManager.logout()
        super.onDestroy()
    }
}
