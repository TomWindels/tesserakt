plugins {
    id("base-config")
}

group = "sparql.bench"

kotlin {
    jvm()
    js {
        nodejs {
            binaries.library()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":testing:bench:sparql:core"))
                implementation(project(":sparql:endpoint:ktor:client"))

                implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:3.1.3")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.1.3")
            }
        }
    }
}
