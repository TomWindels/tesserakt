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

    // silencing expect/actual warnings
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // to deserialize and evaluate datasets
                implementation(project(":utils"))
                implementation(project(":serialization"))
                implementation(project(":sparql"))
                implementation(project(":sparql:runtime")) // providing runtime debug information
                // built-in tests use the dsl for construction
                implementation(project(":rdf:dsl"))
                // to evaluate the results
                implementation(project(":testing:suite"))
                // using the store replay feature for benchmarking
                implementation(project(":benchmarking:store-replay"))
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

// src: https://stackoverflow.com/a/73844513
tasks.withType<Jar> {
    doFirst {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val main by kotlin.jvm().compilations.getting
        manifest {
            attributes(
                "Main-Class" to "Main_jvmKt",
            )
        }
        from({
            main.runtimeDependencyFiles.files.filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
    }
}
