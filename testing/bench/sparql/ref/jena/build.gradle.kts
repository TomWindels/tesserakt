plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":testing:bench:sparql:core"))
                implementation(project(":interop:jena"))
            }
        }
    }
}
