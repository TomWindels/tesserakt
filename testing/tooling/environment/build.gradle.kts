plugins {
    // not distributed as a package
    id("component-conventions")
}

group = "testing"

kotlin {
    jvm()
    js()
    mingwX64()
    linuxX64()
    sourceSets {
        // core modules tested by all test targets
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
            }
        }
    }
}
