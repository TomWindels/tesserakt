import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    // not following any conventions; not published or anything; only testing on the JVM & JS targets
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs()
        binaries.library()
    }
    sourceSets {
        // core modules tested by all test targets
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(project(":extra"))
                implementation(project(":rdf"))
                implementation(project(":rdf-dsl"))
                implementation(project(":sparql"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":interop:jena"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(project(":serialization"))
                implementation(kotlin("test"))
            }
        }
        // dependency is used in the task below; is a main SourceSet for task management purposes
        val jsMain by getting {
            dependencies {
                // the script used for interacting with the external testing suite
                implementation(npm("rdf-test-suite", "1.25.0"))
                // dependencies used when testing logic directly
                implementation(project(":interop:rdfjs"))
                implementation(npm("@comunica/query-sparql", "3.1.2"))
                // awaiting promises
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
            }
        }
    }
}

tasks.create("jsRdfTestSuite") {
    doLast {
        exec {
            workingDir("${project.rootDir}/build/js/packages/tesserakt-tests")
            // w/o -c, nothing gets executed :C
            commandLine("npx", "rdf-test-suite", "kotlin/tesserakt-tests.js", "http://w3c.github.io/rdf-tests/sparql/sparql11/manifest-all.ttl", "-c")
        }
    }
}.also { it.dependsOn("jsRun") }

// required for `jsRun` to behave; does cause constant recompiles, but worth the test accuracy
tasks.named("jsNodeDevelopmentRun").dependsOn("jsProductionLibraryCompileSync")
tasks.named("jsNodeDevelopmentRun").dependsOn("jsDevelopmentLibraryCompileSync")
tasks.named("jsNodeProductionRun").dependsOn("jsProductionLibraryCompileSync")
tasks.named("jsNodeProductionRun").dependsOn("jsDevelopmentLibraryCompileSync")
tasks.named("jsNodeRun").dependsOn("jsProductionLibraryCompileSync")
