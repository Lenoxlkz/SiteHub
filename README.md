# SiteHub

[![build](https://github.com/Lenoxlkz/SiteHub/actions/workflows/build.yml/badge.svg)](https://github.com/Lenoxlkz/SiteHub/actions/workflows/build.yml)

Navegador Android ligero con **motor Firefox real (GeckoView 141)**: extensiones
nativas de Firefox, descargas, privacidad configurable y UA de Chrome para pasar
los desafíos anti-bot (Cloudflare). Una sola actividad, Jetpack Compose, sin
capas innecesarias.

## Características

- **Motor Firefox nativo** (GeckoView): WebExtensions reales, cookies por política
  y CORS según el estándar.
- **Extensiones**: tienda oficial AMO (búsqueda + instalación), por URL `.xpi` o
  archivo local; habilitar/deshabilitar, desinstalar, botón de acción en la barra
  y popups reales. Los permisos se confirman en un diálogo antes de instalar.
- **Descargas**: se guardan en `Descargas/SiteHub` con el cuerpo que entrega el
  motor (cookies intactas); listado con abrir / compartir / eliminar.
- **Privacidad**: cookies (todas / solo del sitio / ninguna) aplicadas en vivo,
  sin bloqueo de contenido que rompa los retos de Cloudflare.
- **Temas**: claro / oscuro / sistema + color dinámico; esquema aplicado al motor.
- **Sitios embebidos** editables y captura de crashes con informe compartible.

## Instalación

Descarga el APK de la [última release](https://github.com/Lenoxlkz/SiteHub/releases/latest):

- `SiteHub-<versión>-arm64.apk` — recomendado (~95 MB, dispositivos arm64).
- `SiteHub-<versión>.apk` — universal (~180 MB, cualquier ABI).

> Las actualizaciones requieren que el APK esté firmado con la **misma clave**.
> Configura un keystore de release fijo (ver más abajo) para no perder la firma
> entre versiones.

## Compilar desde el código

Requisitos: JDK 21 y el wrapper de Gradle incluido.

    ./gradlew :app:assembleRelease :app:testDebugUnitTest

Los APK quedan en `app/build/outputs/apk/release/` (`app-universal-release.apk`
y `app-arm64-v8a-release.apk`).

### Firma de release

El workflow usa un keystore fijo si existen estos **secrets** del repositorio;
si no, cae a la clave debug (solo para pruebas, no permite actualizar):

| Secret | Contenido |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Keystore (PKCS12) codificado en base64 |
| `SIGNING_STORE_PASSWORD` | Contraseña del almacén |
| `SIGNING_KEY_ALIAS` | Alias de la clave |
| `SIGNING_KEY_PASSWORD` | Contraseña de la clave |

Genera el tuyo con `keytool` y súbelo como secrets para mantener una firma estable.

## Arquitectura

- `core/Engine.kt` — runtime GeckoView (vive con el proceso).
- `core/Sessions.kt` — pestañas = GeckoSession; estado de navegación por pestaña.
- `core/extensions/ExtensionsManager.kt` — instalación, permisos, popups y acciones.
- `core/DownloadCenter.kt` — descargas vía MediaStore (Android 10+) o carpeta pública.
- `core/AmoClient.kt` — cliente de la API v4 de addons.mozilla.org.
- `ui/` — Compose (navegador, sitios, extensiones, descargas, ajustes).

## Licencia

Pendiente de definir por el autor. Mientras no se añada un archivo `LICENSE`,
el código queda bajo todos los derechos reservados por defecto.
