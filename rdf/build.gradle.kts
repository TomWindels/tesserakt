plugins {
    id("kmp-package")
}

group = "rdf"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
            }
        }
    }
}
