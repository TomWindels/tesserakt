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
                api(project(":sparql:common"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
