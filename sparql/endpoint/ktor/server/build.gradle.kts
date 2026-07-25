plugins {
    id("mvn-package")
    id("jvm-target")
}

group = "sparql-endpoint"

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                // shared types to interact with sparql endpoints through ktor - the `SparqlEndpoint` type has these as
                //  an API
                api(project(":sparql:endpoint:ktor:core"))
                // extension functions registering endpoints
                implementation(libs.ktor.server.core)
                // the actual querying logic
                implementation(project(":sparql"))
                // whilst ist already included in the non-api sparql module, it's required on an API level to expose the
                //  store API
                api(project(":rdf"))
                // serialization of returned results, exposed as API for endpoint configuration purposes
                api(libs.kotlinx.serialization.json)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
