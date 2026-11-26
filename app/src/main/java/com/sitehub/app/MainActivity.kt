package com.sitehub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

        // Android 6–8.1: permiso de escritura para descargas en almacenamiento
        // público (en Android 10+ se usa MediaStore, sin permiso).
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001
            )
        }

        // Primero captura crashes nativos (libxul, GeckoView) desde logcat:
        // los crashes nativos NO pasan por el handler de Java y por eso antes
        // nunca aparecían en la pantalla de crash.
        val hadNative = CrashReporter.captureNativeOnStart(this)
        val crashText = CrashReporter.pending(this)

        // Si hay crash (nativo o java previo), vamos directo a la pantalla de
        // crash con Copiar/Reiniciar en lugar del diálogo simple.
        if (hadNative || crashText != null) {
            startActivity(Intent(this, CrashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
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
        if (isFinishing) {
            runCatching { Sessions.closeAll() }
        }
        super.onDestroy()
    }
}
