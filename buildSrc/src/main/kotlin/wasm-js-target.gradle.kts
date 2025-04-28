plugins {
    id("base-config")
}

repositories {
    mavenCentral()
}

kotlin {
    // target configuration
    wasmJs {
        nodejs()
        browser()
    }
}
