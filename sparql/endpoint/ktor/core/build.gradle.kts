plugins {
    id("kmp-package")
    alias(libs.plugins.kotlinx.serialization)
}

group = "sparql-endpoint"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // types used in sparql: bindings, quad elements, etc
                api(project(":sparql:common"))
                // public content type instances
                api(libs.ktor.http)
                // to deserialize and evaluate update queries
                implementation(project(":serialization:trig"))
                // support for sparql-results+json mime type
                implementation(libs.ktor.serialization.json)
            }
        }
    }

    androidLibrary {
        lint {
            baseline = file("../lint.xml")
        }
    }
}
