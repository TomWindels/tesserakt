plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":sparql:core"))
                api(project(":sparql:compiler"))
                api(project(":sparql:runtime"))
            }
        }
    }
}
