plugins {
    // not distributed as a package, build targets are manually defined
    id("base-config")
}

group = "sparql"

kotlin {
    jvm()
    js {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // to deserialize and evaluate datasets
                implementation(project(":utils"))
                implementation(project(":serialization:turtle"))
                implementation(project(":serialization:trig"))
                implementation(project(":sparql"))
                implementation(project(":sparql:runtime")) // providing runtime debug information
                // built-in tests use the dsl for construction
                implementation(project(":rdf:dsl"))
                // to evaluate the results
                implementation(project(":testing:tooling:environment"))
                // using the store replay feature for benchmarking
                implementation(project(":testing:tooling:replay-benchmark"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":interop:jena"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(project(":interop:rdfjs"))
                // awaiting promises
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
            }
        }
    }
}
