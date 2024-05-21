//@Suppress("UnstableApiUsage") // centralised repository definitions are incubating
dependencyResolutionManagement {

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    pluginManagement {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
    }
}
