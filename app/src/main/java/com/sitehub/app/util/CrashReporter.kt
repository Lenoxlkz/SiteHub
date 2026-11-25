package com.sitehub.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Captura de crashes de Java: el stack se guarda en un archivo y en el
 * siguiente arranque la app ofrece compartirlo. Sin third-party.
 */
object CrashReporter {

    private const val FILE = "crash_last.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(appContext.filesDir, FILE).writeText(
                    "Hilo: ${thread.name}\n\n" + sw.toString()
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Informe pendiente del arranque anterior, si existe. */
    fun pending(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            .also { if (it != null) file.delete() }
    }
}
