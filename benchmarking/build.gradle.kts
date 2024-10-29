plugins {
    // not distributed as a package, build targets are manually defined
    kotlin("multiplatform")
}

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
                implementation(project(":serialization"))
                implementation(project(":sparql"))
                // built-in tests use the dsl for construction
                implementation(project(":rdf-dsl"))
                // to evaluate the results
                implementation(project(":testing:suite"))
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