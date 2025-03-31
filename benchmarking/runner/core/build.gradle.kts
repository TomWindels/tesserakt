plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(project(":sparql"))
                api(project(":benchmarking:store-replay"))
            }
        }
    }
}
