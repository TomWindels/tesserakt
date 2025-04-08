plugins {
    kotlin("multiplatform")
}

group = "benchmarking.runner"

kotlin {
    js {
        nodejs {
            binaries.library()
        }
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":testing:bench:sparql:core"))
                implementation(project(":interop:rdfjs"))
                implementation(npm("@comunica/query-sparql", "4.1.0"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }
    }
}
