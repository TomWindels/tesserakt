plugins {
    id("kmp-package")
    kotlin("plugin.serialization") version libs.versions.kotlin
}

group = "sparql-endpoint"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // types used in sparql: bindings, quad elements, etc
                api(project(":sparql:common"))
                // public content type instances
                api("io.ktor:ktor-http:3.1.3")
                // to deserialize and evaluate update queries
                implementation(project(":serialization:trig"))
                // support for sparql-results+json mime type
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
            }
        }
    }

    androidLibrary {
        lint {
            baseline = file("../lint.xml")
        }
    }
}
