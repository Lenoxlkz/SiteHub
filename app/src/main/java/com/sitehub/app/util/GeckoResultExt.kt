package com.sitehub.app.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Tiempo por defecto que esperamos a que el motor responda una llamada. */
const val GECKO_CALL_TIMEOUT_MS = 60_000L

/**
 * El motor no respondió a una llamada dentro del tiempo máximo. Sin este
 * límite, cualquier operación contra GeckoView quedaría girando para siempre.
 */
class EngineNotRespondingException(
    val operation: String,
    val timeoutMs: Long
) : Exception(
    "El motor no respondió en ${timeoutMs / 1000}s al $operation"
)

/**
 * Adapter suspend para GeckoResult.
 *
 * Acepta resultados NULL de forma legítima: muchas APIs de GeckoView (p. ej.
 * WebExtensionController.uninstall) devuelven GeckoResult<Void>, que completa
 * con null. El antiguo `value!!` lanzaba NPE dentro del listener, la corrutina
 * nunca reanudaba y la operación "colgaba" hasta el timeout aunque hubiera
 * tenido éxito (desinstalar tardaba SIEMPRE 60 s).
 */
@Suppress("UNCHECKED_CAST")
suspend fun <T> GeckoResult<T>.gvAwait(): T = suspendCancellableCoroutine { cont ->
    this.then(
        object : GeckoResult.OnValueListener<T, Void> {
            override fun onValue(value: T?): GeckoResult<Void>? {
                cont.resume(value as T)
                return null
            }
        },
        object : GeckoResult.OnExceptionListener<Void> {
            override fun onException(exception: Throwable?): GeckoResult<Void>? {
                cont.resumeWithException(
                    exception ?: RuntimeException("GeckoResult falló sin detalle")
                )
                return null
            }
        }
    )
}

/**
 * Igual que [gvAwait] pero con tiempo máximo. Usa withTimeout (no
 * withTimeoutOrNull) para NO confundir un resultado null legítimo
 * (GeckoResult<Void>) con un timeout real.
 */
suspend fun <T> GeckoResult<T>.gvAwaitOrTimeout(
    operation: String,
    timeoutMs: Long = GECKO_CALL_TIMEOUT_MS
): T = try {
    withTimeout(timeoutMs) { gvAwait() }
} catch (e: TimeoutCancellationException) {
    throw EngineNotRespondingException(operation, timeoutMs)
}
