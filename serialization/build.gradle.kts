plugins {
    id("kmp-package")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
                api(project(":serialization:n-triples"))
                api(project(":serialization:turtle"))
                api(project(":serialization:trig"))
                api(project(":serialization:n3"))
            }
        }
    }
}
