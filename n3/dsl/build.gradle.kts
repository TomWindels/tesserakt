plugins {
    id("kmp-package")
}

group = "n3"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":n3"))
            }
        }
    }
}
