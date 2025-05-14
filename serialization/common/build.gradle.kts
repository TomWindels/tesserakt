plugins {
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
            }
        }
    }
}
