plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":n3"))
            }
        }
    }
}
