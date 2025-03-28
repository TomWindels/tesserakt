plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.13"
    kotlin("plugin.allopen") version "2.0.20"
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":common"))
                implementation(project(":rdf"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")

                // also adding types from the runtime we want to benchmark
                implementation(project(":sparql:runtime"))
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("js")
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}
