plugins {
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":utils"))
                implementation(project(":serialization:core"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
