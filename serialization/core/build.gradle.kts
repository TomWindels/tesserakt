plugins {
    // not distributed as a package
    id("package-conventions")
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
