plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
                api(project(":serialization:n-triples"))
                api(project(":serialization:turtle"))
                api(project(":serialization:n3"))
            }
        }
    }
}
