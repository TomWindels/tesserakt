plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    jvm()
    js {
        nodejs {
            binaries.library()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
                implementation(project(":sparql"))
                api(project(":testing:tooling:replay-benchmark"))
            }
        }
    }
}
