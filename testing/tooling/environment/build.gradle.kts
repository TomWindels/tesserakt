plugins {
    // not distributed as a package, but reusing the target platform conventions/configurations
    id("jvm-target")
    id("native-target")
    id("wasm-js-target")
}

group = "testing"

kotlin {
    js {
        nodejs()
    }
    // jvm and native are already configured through the convention plugins
    sourceSets {
        // core modules tested by all test targets
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
            }
        }
    }
}
