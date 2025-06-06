plugins {
    // not distributed as a package, build targets are manually defined
    id("base-config")
    id("io.ktor.plugin") version "3.1.3"
}

group = "sparql-endpoint"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // the endpoint implementation
                implementation(project(":sparql:endpoint:ktor:server"))
                // hosting the actual endpoint
                implementation("io.ktor:ktor-server-core:3.1.3")
                implementation("io.ktor:ktor-server-netty:3.1.3")
                // proper CLI support
                implementation("com.github.ajalt.clikt:clikt:5.0.1")
            }
        }

        // testing the endpoint from a local client
        val jvmTest by getting {
            dependencies {
                // setting up tests
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-server-test-host:3.1.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.1.3")

                // actually creating / processing requests
                implementation(project(":sparql:endpoint:ktor:client"))
            }
        }
    }
}
