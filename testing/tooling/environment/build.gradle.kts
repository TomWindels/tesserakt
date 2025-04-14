plugins {
    // not distributed as a package
    id("base-config")
}

group = "testing"

kotlin {
    jvm()
    js {
        nodejs()
    }
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
