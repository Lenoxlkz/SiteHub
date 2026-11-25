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

object CrashReporter {
    private fun dir(context: Context) = File(context.filesDir, "crash")
    private fun file(context: Context) = File(dir(context), "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    append("SiteHub crash report\n")
                    append("Fecha: $stamp\n")
                    append("Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}\n")
                    append("Hilo: ${thread.name}\n\n")
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

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun pending(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText()?.also { file(context).delete() } }.getOrNull()
}
