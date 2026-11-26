package com.sitehub.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.sitehub.app.BuildConfig
import com.sitehub.app.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captura de crashes de SiteHub.
 *
 * - Crash JAVA/Kotlin: via Thread.setDefaultUncaughtExceptionHandler (handler único).
 * - Crash NATIVO (SIGSEGV/SIGABRT en libxul de GeckoView): se captura al
 *   siguiente arranque leyendo el buffer `crash` de logcat, donde Android
 *   vuelca los "Fatal signal" del proceso. Este handler es la ÚNICA forma
 *   de verlos sin adb ni NDK (tombstone del sistema).
 */
object CrashReporter {
    private var installed = false
    private fun dir(context: Context) = File(context.filesDir, "crash")
    private fun file(context: Context) = File(dir(context), "last_crash.txt")

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    append("SiteHub JAVA crash report
")
                    append("Fecha: $stamp
")
                    append("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
")
                    append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
")
                    append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
")
                    append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}
")
                    append("Hilo: ${thread.name}

")
                    append(sw.toString())
                }
                dir(app).mkdirs()
                file(app).writeText(report)
                runCatching {
                    app.startActivity(Intent(app, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Captura crashes NATIVOS desde logcat al arranque. Llama a este método
     * desde MainActivity.onCreate ANTES de mostrar cualquier UI. Devuelve
     * true si había un crash nativo pendiente de mostrar.
     */
    fun captureNativeOnStart(context: Context): Boolean = runCatching {
        val log = readLogcat("crash", 800)
        if (!log.contains("Fatal signal") && !log.contains("SIGSEGV")
            && !log.contains("SIGABRT") && !log.contains("SIGBUS")) return@runCatching false

        // Evitar mostrar el mismo crash dos veces seguidas.
        val prefs = context.getSharedPreferences("crash_meta", Context.MODE_PRIVATE)
        val key = log.hashCode().toString()
        if (prefs.getString("last_native", null) == key) return@runCatching false
        prefs.edit().putString("last_native", key).apply()

        // Incluir también las últimas líneas del buffer `main` para contexto.
        val extras = runCatching { readLogcat("main", 120) }.getOrDefault("")
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            append("SiteHub NATIVE crash report (logcat)
Capturado: $stamp
")
            append("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
")
            append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
")
            append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
")
            append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}

")
            append("==== buffer `crash` (últimas 800 líneas) ====
").append(log).append("
")
            append("==== buffer `main` (últimas 120 líneas) ====
").append(extras)
        }
        dir(context).mkdirs()
        file(context).writeText(report)
        true
    }.getOrDefault(false)

    private fun readLogcat(buffer: String, lines: Int): String = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-b", buffer, "-t", lines.toString())
            .redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        out
    }.getOrDefault("")

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    /** Lee y borra el informe (lo usa MainActivity antes de lanzar CrashActivity). */
    fun pending(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText()?.also { file(context).delete() } }.getOrNull()
}
