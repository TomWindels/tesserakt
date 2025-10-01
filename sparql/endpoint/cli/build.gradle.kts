plugins {
    // not distributed as a package, build targets are manually defined
    kotlin("jvm")
    id("io.ktor.plugin") version "3.1.3"
}

group = "sparql-endpoint"

kotlin {
    dependencies {
        // the endpoint implementation
        implementation(project(":sparql:endpoint:ktor:server"))
        // used to set an initial file as the in-memory store
        implementation(project(":serialization:trig"))
        // hosting the actual endpoint
        implementation("io.ktor:ktor-server-core:3.1.3")
        implementation("io.ktor:ktor-server-netty:3.1.3")
        implementation("io.ktor:ktor-server-status-pages:3.1.3")
        // proper CLI support
        implementation("com.github.ajalt.clikt:clikt:5.0.1")

        /* test dependencies */

        // setting up tests
        testImplementation(kotlin("test"))
        testImplementation("io.ktor:ktor-server-test-host:3.1.3")
        testImplementation("io.ktor:ktor-client-content-negotiation:3.1.3")

        // actually creating / processing requests
        testImplementation(project(":sparql:endpoint:ktor:client"))
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
