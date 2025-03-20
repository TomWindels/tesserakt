import java.util.*

plugins {
    // not distributed as a package, build targets are manually defined
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // to deserialize and evaluate datasets
                implementation(project(":common"))
                implementation(project(":serialization"))
                implementation(project(":benchmarking:store-replay"))
                // being able to actually execute the queries
                implementation(project(":sparql"))
                implementation(project(":sparql:runtime"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":interop:jena"))
            }
        }
    }
}

val build = layout.buildDirectory
val graphingTarget = build.dir("graphs")

fun local(name: String): String {
    val properties = Properties()
    properties.load(File(rootDir.absolutePath + "/local.properties").inputStream())
    return properties.getProperty(name)
}

val cleanGraphTool = tasks.register("cleanGraphingTool", Exec::class.java) {
    enabled = !graphingTarget.get().asFile.exists()
    workingDir = build.asFile.get()
    commandLine("rm", "-rf", graphingTarget.get().asFile.path)
}

val graphPreparation = tasks.register("prepareGraphingTool", Exec::class.java) {
    enabled = !graphingTarget.get().asFile.exists()
    workingDir = build.asFile.get()
    val url = local("benchmarking.graph.url")
    commandLine("git", "clone", url, "graphs")
}

val graphConfiguration = tasks.register("configureGraphingTool", Exec::class.java) {
    enabled = !graphingTarget.get().file("pyvenv.cfg").asFile.exists()
    workingDir = build.asFile.get()
    commandLine("python", "-m", "venv", "graphs")
}

val graphInstallation = tasks.register("installGraphingTool", Exec::class.java) {
    enabled = !graphingTarget.get().dir("lib").asFile.listFiles()?.singleOrNull { it.isDirectory }?.listFiles()
        ?.singleOrNull { it.name == "site-packages" }?.listFiles()
        .let { it != null && it.any { it.isDirectory && it.name == "pandas" } }
    workingDir = graphingTarget.get().asFile
    commandLine("./bin/pip", "install", "-r", "requirements.txt")
}

val runner = tasks.register("runBenchmark", Exec::class) {
    workingDir = build.asFile.get()
    val jar = build.dir("libs").get().asFile.listFiles()?.singleOrNull { it.extension == "jar" }?.path
    check(jar != null) { "Could not resolve the executable JAR file!" }
    val source = local("benchmarking.input")
    commandLine("java", "-jar", jar, "-i", source, "-o", "${build.get().asFile.path}/benchmark_output/")
}

val graphing = tasks.register("createBenchmarkGraphs", Exec::class.java) {
    val targets = build.dir("benchmark_output").get().asFile.path + "/*"
    workingDir = graphingTarget.get().asFile
    commandLine("./bin/python", "main.py", targets)
}

runner.get().dependsOn(tasks.named("jvmJar"))
runner.get().finalizedBy(graphing)

graphing.get().dependsOn(graphInstallation)
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
