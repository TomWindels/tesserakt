plugins {
    // not distributed as a package
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
                api(project(":serialization:common"))
            }
        }
    }
}
