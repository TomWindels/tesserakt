plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":sparql:core"))
            }
        }
    }
}
