pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        val kotlinVersion = extra["kotlin.version"] as String

        kotlin("multiplatform") version kotlinVersion
    }
}

rootProject.name = "tesserakt"
