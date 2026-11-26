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
    private var installed = false
    private fun dir(context: Context) = File(context.filesDir, "crash")
    private fun file(context: Context) = File(dir(context), "last_crash.txt")

    private fun header(kind: String): String = buildString {
        appendLine("SiteHub " + kind + " crash report")
        appendLine("Fecha: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        appendLine("Versión: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
        appendLine("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        appendLine("Dispositivo: " + Build.MANUFACTURER + " " + Build.MODEL)
        appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString())
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter(); throwable.printStackTrace(PrintWriter(sw))
                val report = header("JAVA") + "Hilo: " + thread.name +
                    System.lineSeparator() + System.lineSeparator() + sw.toString()
                dir(app).mkdirs(); file(app).writeText(report)
                runCatching {
                    app.startActivity(Intent(app, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun captureNativeOnStart(context: Context): Boolean = runCatching {
        val text = buildString {
            append(readLogcat("crash", 400))
            appendLine()
            appendLine("---- errores recientes ----")
            append(readLogcat("main", 150))
        }
        if (!text.contains("Fatal signal") && !text.contains("SIGSEGV") && !text.contains("SIGABRT")) return false
        val prefs = context.getSharedPreferences("crash_meta", Context.MODE_PRIVATE)
        val key = text.hashCode().toString()
        if (prefs.getString("last_native", null) == key) return false
        prefs.edit().putString("last_native", key).apply()
        val report = header("NATIVE (logcat)") + System.lineSeparator() + text
        dir(context).mkdirs(); file(context).writeText(report)
        true
    }.getOrDefault(false)

    private fun readLogcat(buffer: String, lines: Int): String = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-b", buffer, "-t", lines.toString())
            .redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor(); out
    }.getOrDefault("")

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun pending(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText()?.also { file(context).delete() } }.getOrNull()
}
