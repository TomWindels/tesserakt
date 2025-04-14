plugins {
    id("kmp-package")
}

group = "n3"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
            }
        }
    }
}
