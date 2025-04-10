plugins {
    id("kmp-package")
}

group = "rdf"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
                implementation(project(":stream:ldes"))
            }
        }
    }
}
