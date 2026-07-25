plugins {
    id("kmp-package")
}

group = "rdf"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":rdf"))
            }
        }
    }
}
