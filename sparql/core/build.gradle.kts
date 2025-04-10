plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
                api(project(":sparql:common"))
            }
        }
    }
}
