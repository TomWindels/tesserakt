import java.util.*

plugins {
    // not distributed as a package, build targets are manually defined
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs {
            passProcessArgvToMainFunction()
            binaries.executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // to deserialize and evaluate datasets
                implementation(project(":common"))
                implementation(project(":serialization"))
                // being able to actually execute the queries
                implementation(project(":benchmarking:runner:core"))
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
                // TODO: properties-based
                implementation(project(":benchmarking:runner:ref:blazegraph"))
                implementation(project(":benchmarking:runner:ref:jena"))
//                implementation(project(":benchmarking:runner:ref:rdfox"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(project(":benchmarking:runner:ref:comunica"))
            }
        }
    }
}

val build = layout.buildDirectory
val graphingTarget = build.dir("graphs")

fun local(name: String): String? {
    val properties = Properties()
    properties.load(File(rootDir.absolutePath + "/local.properties").inputStream())
    return properties.getProperty(name, null)
}

val cleanGraphTool = tasks.register("cleanGraphingTool", Exec::class.java) {
    enabled = !graphingTarget.get().asFile.exists()
    workingDir = build.asFile.get()
    commandLine("rm", "-rf", graphingTarget.get().asFile.path)
}

val cleanBenchmarkResults = tasks.register("cleanBenchmarkResults", Exec::class.java) {
    workingDir = build.asFile.get()
    commandLine("rm", "-rf", build.dir("benchmark_output").get().asFile.path)
}

val graphPreparation = tasks.register("prepareGraphingTool", Exec::class.java) {
    group = "benchmarking"
    enabled = !graphingTarget.get().asFile.exists()
    workingDir = build.asFile.get()
    val url = local("benchmarking.graph.url")
        ?: throw IllegalStateException("No benchmark graph source configured! Please add `benchmarking.graph.url=<url>` to `${project.rootProject.rootDir.path}/local.properties`!")
    commandLine("git", "clone", url, "graphs")
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

val runnerJvm = tasks.register("runBenchmarkJvm", Exec::class) {
    group = "benchmarking"
    workingDir = build.asFile.get()
    val jar = build.dir("libs").get().file("runner-jvm-${version}.jar").asFile.path
    val source = local("benchmarking.input")
        ?: throw IllegalStateException("No benchmark input configured! Please add `benchmarking.input=<path/to/dataset>` to `${project.rootProject.rootDir.path}/local.properties`!")
    commandLine("java", "-jar", jar, "-i", source, "-o", "${build.get().asFile.path}/benchmark_output/jvm/", "--compare-implementations")
}

runnerJvm.get().dependsOn(tasks.named("jvmJar"))

val runnerJs = tasks.register("runBenchmarkJs", Exec::class) {
    group = "benchmarking"
    workingDir = rootDir
    // retrieved & configured through the "kotlinNodejsSetup" task
    val node = "${File("${gradle.gradleUserHomeDir}/nodejs").listFiles().single { file -> file.isDirectory }}/bin/node"
    val file = "build/js/packages/tesserakt-benchmarking-runner/kotlin/tesserakt-benchmarking-runner.js"
    val source = local("benchmarking.input")
        ?: throw IllegalStateException("No benchmark input configured! Please add `benchmarking.input=<path/to/dataset>` to `${project.rootProject.rootDir.path}/local.properties`!")
    commandLine(node, file, "-i", source, "-o", "${build.get().asFile.path}/benchmark_output/js/", "--compare-implementations")
}

runnerJs.get().dependsOn(tasks.named("kotlinNodeJsSetup"))
runnerJs.get().dependsOn(tasks.named("assemble"))

val runner = tasks.register("runBenchmark") {
    group = "benchmarking"
}

runner.get().dependsOn(runnerJvm, runnerJs)

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

runnerJvm.get().finalizedBy(graphingJvm)
runnerJs.get().finalizedBy(graphingJs)
runner.get().finalizedBy(combinedGraphing)

graphingJvm.get().dependsOn(graphInstallation)
graphingJs.get().dependsOn(graphInstallation)
graphInstallation.get().dependsOn(graphConfiguration)
graphConfiguration.get().dependsOn(graphPreparation)

tasks.named("clean").get().finalizedBy(cleanGraphTool)

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
