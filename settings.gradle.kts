pluginManagement {
    repositories {
        // Espejos Aliyun primero: resuelven aunque Maven Central limite por
        // tasa; los repos oficiales quedan como respaldo.
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        // Motor Firefox: solo está en el repositorio oficial de Mozilla.
        maven { url = uri("https://maven.mozilla.org/maven2") }
    }
}

rootProject.name = "SiteHub"
include(":app")
