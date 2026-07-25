plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":utils"))

                api(project(":sparql:core"))
                api(project(":sparql:compiler"))
                api(project(":sparql:runtime"))
            }
        }
    }
}
