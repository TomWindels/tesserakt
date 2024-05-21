dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files(File("./../gradle/libraries.versions.toml")))
        }
    }
}
