plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    jvm()
    // reports say this has better performance for BlazeGraph
    jvmToolchain(9)

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":benchmarking:runner:core"))
                implementation("com.blazegraph:bigdata-core:2.1.4")
            }
        }
    }
}
