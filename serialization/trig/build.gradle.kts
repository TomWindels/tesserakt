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
                api(project(":serialization:common"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":rdf:dsl"))
                implementation(kotlin("test"))
            }
        }
    }
}
