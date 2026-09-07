plugins {
    id("kmp-package")
}

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
