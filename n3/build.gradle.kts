plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":rdf"))
            }
        }
    }
}
