plugins {
    id("base-config")
}

repositories {
    mavenCentral()
}

kotlin {
    // target configuration
    @Suppress("OPT_IN_USAGE")
    wasmJs {
        nodejs()
        browser {
            testTask {
                // we cannot guarantee every system has the same set of browsers available, so the browser target
                //  should not be tested
                enabled = false
            }
        }
    }
}
