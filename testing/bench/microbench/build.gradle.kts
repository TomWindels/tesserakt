plugins {
    id("base-config")
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.kotlinx.allopen)
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":utils"))
                implementation(project(":rdf"))
                implementation(libs.kotlinx.benchmark)

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
