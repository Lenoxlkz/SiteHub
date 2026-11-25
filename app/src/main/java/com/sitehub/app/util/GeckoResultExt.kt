package com.sitehub.app.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Tiempo por defecto que esperamos a que el motor responda una llamada. */
const val GECKO_CALL_TIMEOUT_MS = 60_000L

/**
 * El motor no respondió a una llamada dentro del tiempo máximo. Sin este
 * límite, cualquier operación contra GeckoView quedaría girando para siempre
 * (el clásico "bucle de carga" al instalar extensiones).
 */
class EngineNotRespondingException(
    val operation: String,
    val timeoutMs: Long
) : Exception(
    "El motor no respondió en ${timeoutMs / 1000}s al $operation"
)

/** Adapter suspend para GeckoResult: permite `resultado.gvAwait()` en corrutinas. */
suspend fun <T> GeckoResult<T>.gvAwait(): T = suspendCancellableCoroutine { cont ->
    this.then(
        object : GeckoResult.OnValueListener<T, Void> {
            override fun onValue(value: T?): GeckoResult<Void>? {
                cont.resume(value!!)
                return null
            }
        },
        object : GeckoResult.OnExceptionListener<Void> {
            override fun onException(exception: Throwable): GeckoResult<Void>? {
                cont.resumeWithException(
                    exception ?: RuntimeException("GeckoResult falló sin detalle")
                )
                return null
            }
        }
    )
}

/**
 * Igual que [gvAwait], pero con tiempo máximo: si el motor no resuelve en
 * [timeoutMs], aborta con [EngineNotRespondingException] en lugar de colgar.
 */
suspend fun <T> GeckoResult<T>.gvAwaitOrTimeout(
    operation: String,
    timeoutMs: Long = GECKO_CALL_TIMEOUT_MS
): T {
    val value = withTimeoutOrNull(timeoutMs) { gvAwait() }
    return value ?: throw EngineNotRespondingException(operation, timeoutMs)
}
