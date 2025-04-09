import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    kotlin("multiplatform")
}

kotlin {
    // only configuring JS target, as that's what the suite uses (JS interface)
    js {
        nodejs()
        binaries.executable()
    }
    sourceSets {
        // dependency is used in the task below; is a main SourceSet for task management purposes
        val jsMain by getting {
            dependencies {
                // the script used for interacting with the external testing suite
                implementation(npm("rdf-test-suite", "1.25.0"))
                // dependencies used when testing logic directly
                implementation(project(":sparql"))
                implementation(project(":sparql:core")) // required to analyse the compiled query
                implementation(project(":interop:rdfjs"))
                implementation(npm("@comunica/query-sparql", "3.1.2"))
                // awaiting promises
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
            }
        }
    }
}

val rdfSuite = tasks.register("rdf-test-suite-js", Exec::class.java) {
    File("${project.rootDir.absolutePath}/.cache/rdf-test-suite").mkdirs()
    workingDir("${project.rootDir}/build/js/packages/tesserakt-testing-rdf-test-suite-js")
    commandLine("node", "../../node_modules/rdf-test-suite/bin/Runner.js", "kotlin/tesserakt-testing-rdf-test-suite-js.js", "http://w3c.github.io/rdf-tests/sparql/sparql11/manifest-all.ttl", "-c", "../../../../.cache/rdf-test-suite/", "-s", "http://www.w3.org/TR/sparql11-query/")
}

rdfSuite.dependsOn("jsRun")

// required for `jsRun` to behave; does cause constant recompiles, but worth the test correctness
tasks.named("jsNodeDevelopmentRun").dependsOn("jsProductionExecutableCompileSync")
tasks.named("jsNodeDevelopmentRun").dependsOn("jsDevelopmentExecutableCompileSync")
tasks.named("jsNodeProductionRun").dependsOn("jsProductionExecutableCompileSync")
tasks.named("jsNodeProductionRun").dependsOn("jsDevelopmentExecutableCompileSync")
tasks.named("jsNodeRun").dependsOn("jsProductionExecutableCompileSync")
