import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    // not following any conventions; not published or anything; only testing on the JVM & JS targets
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs()
        binaries.executable()
    }
    sourceSets {
        // core modules tested by all test targets
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(project(":extra"))
                implementation(project(":rdf"))
                implementation(project(":rdf-dsl"))
                implementation(project(":serialization"))
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

tasks.create("prepareBenchmark") {
    doLast {
        exec {
            workingDir(project.rootDir)
            commandLine("git", "submodule", "update", "--remote")
        }
        exec {
            val resources = "${projectDir.absolutePath}/src/commonMain/resources"
            workingDir("$resources/benchmarks")
            commandLine("bash", "configure.sh")
        }
    }
}

// required for `jsRun` to behave; does cause constant recompiles, but worth the test accuracy
tasks.named("jsNodeDevelopmentRun").dependsOn("jsProductionExecutableCompileSync")
tasks.named("jsNodeDevelopmentRun").dependsOn("jsDevelopmentExecutableCompileSync")
tasks.named("jsNodeProductionRun").dependsOn("jsProductionExecutableCompileSync")
tasks.named("jsNodeProductionRun").dependsOn("jsDevelopmentExecutableCompileSync")
tasks.named("jsNodeRun").dependsOn("jsProductionExecutableCompileSync")

// making sure the benchmark preparations are done when running these
tasks.named("jsNodeRun").dependsOn("prepareBenchmark")
tasks.withType<JavaExec>().matching { it.name == "jvmRun" }.configureEach {
    val resources = "${projectDir.absolutePath}/src/commonMain/resources"
    args("$resources/benchmarks/watdiv/dataset.nt", "$resources/benchmarks/watdiv/queries/S1.txt")
    dependsOn("prepareBenchmark")
}

