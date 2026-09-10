plugins {
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":rdf"))

                implementation(project(":utils"))
            }
        }
    }
}
