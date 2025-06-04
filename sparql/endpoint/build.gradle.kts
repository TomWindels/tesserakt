plugins {
    // not distributed as a package, build targets are manually defined
    id("base-config")
    kotlin("plugin.serialization") version libs.versions.kotlin
}

group = "sparql"

kotlin {
    jvm()
    // only the Kotlin/Native target could also be added w/ ktor-server support;
    //  supporting the nodejs platform through ktor is not possible

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // to deserialize and evaluate update queries
                implementation(project(":serialization:trig"))
                // to evaluate select queries
                implementation(project(":sparql"))
                // providing the actual endpoint
                implementation("io.ktor:ktor-server-core:3.1.3")
                implementation("io.ktor:ktor-server-netty:3.1.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
                implementation("io.ktor:ktor-server-content-negotiation:3.1.3")
                // proper CLI support
                implementation("com.github.ajalt.clikt:clikt:5.0.1")
            }
        }
    }
}
