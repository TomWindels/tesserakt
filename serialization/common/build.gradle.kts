plugins {
    id("package-conventions")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":serialization:core"))
            }
        }
    }
}
