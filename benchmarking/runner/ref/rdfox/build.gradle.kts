plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":benchmarking:runner:core"))
                implementation(files("libs/JRDFox.jar"))
                implementation(project(":serialization:trig"))
            }
        }
    }
}
