plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sitehub.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sitehub.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    // APK por arquitectura: arm64 pesa ~95 MB (el universal con las tres
    // ABIs pesa ~180 MB). Se generan ambos; el universal queda de respaldo.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    // Firma de release ESTABLE: si el workflow inyecta un keystore fijo
    // (secrets SIGNING_*), se usa ese; si no, cae a debug solo como respaldo.
    // La clave debug de CI se regenera en cada run y rompe las actualizaciones
    // (INSTALL_FAILED_UPDATE_INCOMPATIBLE): cada APK salía con otra firma.
    val releaseKeystore = System.getenv("SIGNING_KEYSTORE_FILE")
    val hasReleaseSigning = releaseKeystore != null && file(releaseKeystore).exists()
    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            // R8 + shrinkResources DESACTIVADOS: con GeckoView provocaban un
            // crash en arranque que el CI (que solo compila) no detecta. Es la
            // misma configuración que funcionaba en v1.0.0. Se puede reactivar
            // más adelante con reglas keep probadas en un dispositivo real.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // El lint vital sobre el AAR de GeckoView (~90 MB) desborda memoria;
        // el análisis estático no aporta aquí y bloquea el release.
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

dependencies {
    // Motor Firefox real (GeckoView 141): WebExtensions nativas, cookies,
    // Cloudflare y CORS al estándar de un navegador completo.
    implementation("org.mozilla.geckoview:geckoview:141.0.20250806102122")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // org.json real para las pruebas locales (el stub de android.jar lanza
    // RuntimeException "Stub!" en JVM sin dispositivo).
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
