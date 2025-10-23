plugins {
    // not distributed as a package, build targets are manually defined
    kotlin("jvm")
    id("io.ktor.plugin") version "3.1.3"
    id("org.graalvm.buildtools.native") version "0.11.1"
}

group = "sparql-endpoint"

kotlin {
    dependencies {
        // the endpoint implementation
        implementation(project(":sparql:endpoint:ktor:server"))
        // used to set an initial file as the in-memory store
        implementation(project(":serialization:trig"))
        // hosting the actual endpoint
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.cio)
        implementation(libs.ktor.server.statusPages)
        // proper CLI support
        implementation(libs.clikt)

        /* test dependencies */

        // setting up tests
        testImplementation(kotlin("test"))
        testImplementation(libs.ktor.serverTestHost)
        testImplementation(libs.ktor.client.contentNegotiation)

        // actually creating / processing requests
        testImplementation(project(":sparql:endpoint:ktor:client"))
    }
}

graalvmNative {
    binaries {

        named("main") {
            fallback.set(false)
            verbose.set(true)

            // src: https://github.com/HewlettPackard/kraal/issues/5
            buildArgs.add("--initialize-at-build-time=io.ktor,kotlinx,kotlin,org.slf4j")

            // src: https://github.com/ktorio/ktor-samples/blob/main/graalvm/build.gradle.kts
            buildArgs.add("-H:+InstallExitHandlers")
            buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
            buildArgs.add("-H:+ReportExceptionStackTraces")

            imageName.set("tesserakt-endpoint")
        }
    }
}

application {
    mainClass.set("dev.tesserakt.sparql.endpoint.server.MainKt")
}

ktor {
    fatJar {
        archiveFileName.set("tesserakt-endpoint.jar")
    }
}
