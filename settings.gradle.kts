pluginManagement {
    repositories {
        // Repos oficiales primero (integridad de los artefactos); los espejos
        // Aliyun quedan como respaldo por si Maven Central limita por tasa.
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Motor Firefox: solo está en el repositorio oficial de Mozilla.
        maven { url = uri("https://maven.mozilla.org/maven2") }
        // Espejos de respaldo.
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "SiteHub"
include(":app")
