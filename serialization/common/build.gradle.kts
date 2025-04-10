plugins {
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":rdf"))
            }
        }
    }
}
