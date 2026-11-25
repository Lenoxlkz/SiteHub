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
        versionCode = 1
        versionName = "1.0.0"

        // Solo arm64: el APK pesa ~92-97 MB en vez de ~160 MB y es la
        // arquitectura de todos los móviles actuales (incluido tu Honor).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firma con la clave de depuración para que el APK se pueda
            // instalar directamente sin proceso de release privado.
            signingConfig = signingConfigs.getByName("debug")
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
}
