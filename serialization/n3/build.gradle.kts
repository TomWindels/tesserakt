plugins {
    id("package-conventions")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":serialization:core"))
                implementation(project(":n3"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":rdf-dsl"))
                implementation(kotlin("test"))
            }
        }
    }
}
