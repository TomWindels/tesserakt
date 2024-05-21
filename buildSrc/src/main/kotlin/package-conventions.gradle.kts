plugins {
    // expanding upon existing core package
    id("core-package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
            }
        }
    }
}
