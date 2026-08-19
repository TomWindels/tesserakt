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
                implementation(project(":n3"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":n3:dsl"))
                implementation(kotlin("test"))
            }
        }
    }
}
