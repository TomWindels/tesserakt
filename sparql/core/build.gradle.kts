plugins {
    id("package-conventions")
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
