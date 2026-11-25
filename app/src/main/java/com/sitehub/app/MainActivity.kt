package com.sitehub.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sitehub.app.core.Engine
import com.sitehub.app.core.Sessions
import com.sitehub.app.ui.Nav
import com.sitehub.app.ui.SiteHubTheme
import com.sitehub.app.util.CrashReporter

/**
 * Actividad única de SiteHub (Compose). El botón "atrás" del sistema navega
 * dentro del sitio web cuando la pestaña Inicio tiene historial.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = CrashReporter.pending(this)
        if (crash != null) {
            AlertDialog.Builder(this)
                .setTitle("SiteHub se cerró inesperadamente")
                .setMessage("¿Compartir el informe para diagnosticarlo?")
                .setPositiveButton("Compartir") { _, _ ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, crash)
                    }
                    startActivity(Intent.createChooser(send, "Informe de crash"))
                }
                .setNegativeButton("Descartar", null)
                .show()
        }

        setContent {
            SiteHubTheme {
                com.sitehub.app.ui.AppRoot()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Nav.tab == Nav.Tab.INICIO && Sessions.canGoBack) {
            Sessions.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        // Al cerrar la actividad, la vista de Gecko se suelta (las sesiones
        // siguen vivas en memoria solo mientras el proceso siga adelante).
        if (isFinishing) {
            runCatching { Sessions.closeAll() }
            runCatching { Engine.runtime?.shutdown() }
        }
        super.onDestroy()
    }
}
