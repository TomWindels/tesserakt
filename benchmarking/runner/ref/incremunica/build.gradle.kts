plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    js()

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":benchmarking:runner:core"))
            }
        }
    }
}
