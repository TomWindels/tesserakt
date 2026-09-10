plugins {
    id("kmp-package")
}

group = "rdf"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":utils"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
