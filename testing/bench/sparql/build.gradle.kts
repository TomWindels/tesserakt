plugins {
    // not distributed as a package, build targets are manually defined
    kotlin("jvm")
}

group = "sparql.bench"

dependencies {
    implementation(project(":utils"))
    implementation(project(":sparql"))
    implementation(project(":testing:tooling:replay-benchmark"))
    // to deserialize and evaluate datasets
    implementation(project(":utils"))
    implementation(project(":serialization:trig"))
    // necessary to properly launch the coroutines associated with the execution
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    // CLI implementation
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    // required for the actual endpoint client implementation
    implementation(project(":sparql:endpoint:ktor:client"))
    implementation("io.ktor:ktor-client-content-negotiation:3.1.3")

    implementation("io.ktor:ktor-client-java:3.1.3")
}

tasks.register("buildFatJar", Jar::class) {
    archiveBaseName = "sparql-bench"
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks["jar"] as CopySpec)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
