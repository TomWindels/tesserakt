import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalMainFunctionArgumentsDsl

plugins {
    // not distributed as a package, build targets are manually defined
    id("base-config")
}

group = "sparql.bench"

kotlin {
    jvm()
    js {
        nodejs {
            @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
            passProcessArgvToMainFunction()
            binaries.executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // to deserialize and evaluate datasets
                implementation(project(":utils"))
                implementation(project(":serialization:trig"))
                // being able to actually execute the queries
                implementation(project(":testing:bench:sparql:core"))
                // necessary to properly launch the coroutines associated with the execution
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                // required to get references from child implementors at runtime
                implementation(kotlin("reflect"))
                // further used in the reflection implementation to detect all reference implementations
                implementation("com.google.guava:guava:33.4.6-jre")
                if (project.hasEnabled("bench.sparql.blazegraph")) {
                    implementation(project(":testing:bench:sparql:ref:blazegraph"))
                }
                if (project.hasEnabled("bench.sparql.jena")) {
                    implementation(project(":testing:bench:sparql:ref:jena"))
                }
                if (project.hasEnabled("bench.sparql.rdfox")) {
                    implementation(project(":testing:bench:sparql:ref:rdfox"))
                }
            }
        }
        val jsMain by getting {
            dependencies {
                if (project.hasEnabled("bench.sparql.comunica")) {
                    implementation(project(":testing:bench:sparql:ref:comunica"))
                }
            }
        }
    }
}

val benchmarkingInput = project.local("benchmarking.input")
val graphRepoUrl = project.local("benchmarking.graph.url")
val benchmarkingEnabled = benchmarkingInput != null && File(benchmarkingInput).exists()

val build = layout.buildDirectory
val graphingTarget = build.dir("graphs")

val cleanGraphTool = tasks.register("cleanGraphingTool", Delete::class.java) {
    delete(graphingTarget.get())
}

val cleanBenchmarkResults = tasks.register("cleanBenchmarkResults", Delete::class.java) {
    delete(build.dir("benchmark_output").get())
}

if (benchmarkingEnabled) {
    setupBenchmarkTasks()
    val graphingEnabled = graphRepoUrl != null
    if (graphingEnabled) {
        setupGraphingTasks()
    } else {
        println("w: No benchmark graph source configured! Please add `benchmarking.graph.url=<url>` to file://${project.rootProject.rootDir.path}/local.properties so Gradle graphing tasks can be generated!")
    }
} else {
    println("w: No benchmark input configured! Please add `benchmarking.input=<path/to/dataset>` to file://${project.rootProject.rootDir.path}/local.properties so benchmarking Gradle tasks can be generated!")
}

fun setupBenchmarkTasks() {
    // src: https://slack-chats.kotlinlang.org/t/486856/anyone-knows-how-to-create-gradle-javaexec-configuration-for#20242df1-da93-4272-8f2e-168a8891a398
    val jvmJar by tasks.existing
    val jvmRuntimeClasspath by configurations.existing

    val runnerJvm = tasks.register("runBenchmarkJvm", JavaExec::class) {
        group = "benchmarking"
        mainClass.set("Main_jvmKt")
        classpath(jvmJar, jvmRuntimeClasspath)
        args("-i", benchmarkingInput, "-o", "${build.get().asFile.path}/benchmark_output/jvm/", "--compare-implementations")
    }

    val runnerJs = tasks.register("runBenchmarkJs", Exec::class) {
        group = "benchmarking"
        workingDir = rootDir
        // retrieved & configured through the "kotlinNodejsSetup" task
        val node = "${File("${gradle.gradleUserHomeDir}/nodejs").listFiles().single { file -> file.isDirectory }}/bin/node"
        val file = "build/js/packages/tesserakt-benchmarking-runner/kotlin/tesserakt-benchmarking-runner.js"
        commandLine(node, file, "-i", benchmarkingInput, "-o", "${build.get().asFile.path}/benchmark_output/js/", "--compare-implementations")
    }

    runnerJs.get().dependsOn(tasks.named("kotlinNodeJsSetup"))
    runnerJs.get().dependsOn(tasks.named("assemble"))

    val runner = tasks.register("runBenchmark") {
        group = "benchmarking"
    }

    runner.get().dependsOn(runnerJvm, runnerJs)
}

fun setupGraphingTasks() {
    val graphPreparation = tasks.register("prepareGraphingTool", Exec::class.java) {
        group = "benchmarking"
        enabled = !graphingTarget.get().asFile.exists()
        workingDir = build.asFile.get()
        commandLine("git", "clone", graphRepoUrl, "graphs")
    }

    val graphConfiguration = tasks.register("configureGraphingTool", Exec::class.java) {
        group = "benchmarking"
        enabled = !graphingTarget.get().file("pyvenv.cfg").asFile.exists()
        workingDir = build.asFile.get()
        commandLine("python", "-m", "venv", "graphs")
    }

    val graphInstallation = tasks.register("installGraphingTool", Exec::class.java) {
        group = "benchmarking"
        enabled = !graphingTarget.get().dir("lib").asFile.listFiles()?.singleOrNull { it.isDirectory }?.listFiles()
            ?.singleOrNull { it.name == "site-packages" }?.listFiles()
            .let { it != null && it.any { it.isDirectory && it.name == "pandas" } }
        workingDir = graphingTarget.get().asFile
        commandLine("./bin/pip", "install", "-r", "requirements.txt")
    }

    val graphingJvm = tasks.register("createBenchmarkGraphsJvm", Exec::class.java) {
        group = "benchmarking"
        val targets = build.dir("benchmark_output").get().asFile.path + "/jvm/*"
        workingDir = graphingTarget.get().asFile
        commandLine("./bin/python", "single_graph.py", targets)
    }

    val graphingJs = tasks.register("createBenchmarkGraphsJs", Exec::class.java) {
        group = "benchmarking"
        val targets = build.dir("benchmark_output").get().asFile.path + "/js/*"
        workingDir = graphingTarget.get().asFile
        commandLine("./bin/python", "single_graph.py", targets)
    }

    val combinedGraphing = tasks.register("createBenchmarkGraphs", Exec::class.java) {
        group = "benchmarking"
        val targets = build
            .dir("benchmark_output")
            .get()
            .asFile
            .path
            .let { base -> arrayOf("$base/js/*", "$base/jvm/*") }
        workingDir = graphingTarget.get().asFile
        commandLine("./bin/python", "multi_graph.py", *targets)
    }

    graphingJvm.get().dependsOn(graphInstallation)
    graphingJs.get().dependsOn(graphInstallation)
    graphInstallation.get().dependsOn(graphConfiguration)
    graphConfiguration.get().dependsOn(graphPreparation)

    // the execution tasks should also be created, so we can find them by name
    tasks.named("runBenchmarkJvm").get().finalizedBy(graphingJvm)
    tasks.named("runBenchmarkJs").get().finalizedBy(graphingJs)
    tasks.named("runBenchmark").get().finalizedBy(combinedGraphing)
}

tasks.named("clean").get().finalizedBy(cleanGraphTool)
