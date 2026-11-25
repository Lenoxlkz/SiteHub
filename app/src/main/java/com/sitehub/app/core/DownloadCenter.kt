package com.sitehub.app.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse

/**
 * Gestión de descargas: cualquier archivo que un sitio intente descargar
 * (octet-stream, attachment, octetos desconocidos) se guarda en la carpeta
 * pública "Descargas/SiteHub" del dispositivo y queda listado aquí, con
 * acciones de abrir, compartir y eliminar.
 *
 * Gecko entrega el cuerpo de la descarga en WebResponse.body: se guarda
 * directo (sin segunda petición), manteniendo cookies y sesión intactas —
 * también funciona tras pasar un desafío de Cloudflare.
 */
object DownloadCenter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Descargas conocidas, para la pantalla de Descargas. */
    var items by mutableStateOf<List<DownloadItem>>(emptyList())
        private set

    /** Último evento para avisar en la UI (nombre del archivo guardado). */
    var lastSaved by mutableStateOf<String?>(null)
        private set

    data class DownloadItem(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String,
        val whenMs: Long
    )

    private const val SUBDIR = "SiteHub"

    fun init(context: Context) {
        refresh(context.applicationContext)
    }

    fun consumeLastSaved() { lastSaved = null }

    /** Guarda la descarga que GeckoView entrega (se llama por cada onExternalResponse). */
    fun save(response: WebResponse) {
        scope.launch {
            val name = fileNameFor(response)
            val saved = runCatching { saveStream(name, response) }.getOrNull()
            if (saved != null) {
                lastSaved = name
                refreshInternal()
            } else {
                lastSaved = "⚠ No se pudo guardar «$name»"
            }
        }
    }

    private fun fileNameFor(response: WebResponse): String {
        // 1) Content-Disposition; 2) nombre de la URL; 3) genérico.
        val disposition = response.headers["Content-Disposition"]
        disposition?.let {
            val match = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(it)
            match?.groupValues?.get(1)?.takeIf { n -> n.isNotBlank() }?.let { return sanitize(it) }
        }
        val fromUri = response.uri.substringBefore('?').substringAfterLast('/')
        if (fromUri.isNotBlank() && fromUri.contains('.')) return sanitize(fromUri)
        val ext = when (response.headers["Content-Type"]) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "application/zip" -> ".zip"
            else -> ".bin"
        }
        return "descarga_${System.currentTimeMillis()}$ext"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)

    private fun saveStream(name: String, response: WebResponse): Uri {
        val body = response.body ?: error("La descarga llegó sin cuerpo (stream vacío)")
        val mime = response.headers["Content-Type"] ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = appContext().contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: error("No se pudo crear la entrada en Descargas")
            resolver.openOutputStream(uri)?.use { out ->
                body.copyTo(out, 64 * 1024)
            } ?: error("No se pudo escribir la descarga")
            return uri
        } else {
            // Android 8 o anterior: carpeta pública clásica (requiere permiso
            // WRITE_EXTERNAL_STORAGE concedido).
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                SUBDIR
            )
            dir.mkdirs()
            val file = File(dir, name)
            file.outputStream().use { out -> body.copyTo(out, 64 * 1024) }
            return Uri.fromFile(file)
        }
    }

    /** Lista las descargas guardadas (MediaStore en Android 10+, carpeta antes). */
    fun refresh(context: Context) {
        appContextRef = context.applicationContext
        scope.launch { refreshInternal() }
    }

    private var appContextRef: Context? = null

    private fun appContext(): Context =
        appContextRef ?: error("DownloadCenter no inicializado")

    private suspend fun refreshInternal() = withContext(Dispatchers.IO) {
        val ctx = appContext()
        val found = mutableListOf<DownloadItem>()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                ctx.contentResolver.query(
                    collection,
                    projection,
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                    arrayOf("%" + Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR + "%"),
                    MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        found.add(
                            DownloadItem(
                                uri = uri,
                                displayName = cursor.getString(1) ?: "archivo",
                                sizeBytes = cursor.getLong(2),
                                mimeType = cursor.getString(3) ?: "application/octet-stream",
                                whenMs = cursor.getLong(4) * 1000L
                            )
                        )
                    }
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    SUBDIR
                )
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                    found.add(
                        DownloadItem(
                            uri = Uri.fromFile(f),
                            displayName = f.name,
                            sizeBytes = f.length(),
                            mimeType = "application/octet-stream",
                            whenMs = f.lastModified()
                        )
                    )
                }
            }
        }
        items = found
    }

    /** Elimina una descarga y devuelve si se logró. */
    fun delete(item: DownloadItem): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29 && item.uri.scheme == "content") {
            appContext().contentResolver.delete(item.uri, null, null) > 0
        } else {
            File(item.uri.path ?: return false).delete()
        }
    }.getOrDefault(false).also { scope.launch { refreshInternal() } }

    /** Intent para abrir el archivo con una app externa. */
    fun openIntent(item: DownloadItem): Intent {
        val ctx = appContext()
        return if (Build.VERSION.SDK_INT >= 29 && item.uri.scheme == "content") {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            val file = File(item.uri.path ?: item.uri.toString())
            val uri = FileProvider.getUriForFile(ctx, "com.sitehub.app.files", file)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /** Intent para compartir el archivo. */
    fun shareIntent(item: DownloadItem): Intent {
        val ctx = appContext()
        val uri = if (Build.VERSION.SDK_INT >= 29 && item.uri.scheme == "content") {
            item.uri
        } else {
            FileProvider.getUriForFile(ctx, "com.sitehub.app.files", File(item.uri.path!!))
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
