plugins {
    id("kmp-package")
}

group = "sparql-endpoint"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // shared types to interact with sparql endpoints through ktor
                implementation(project(":sparql:endpoint:ktor:core"))
                // used in serializing sparql update requests
                implementation(project(":serialization:trig"))
                // used to provide DSL support when dealing with sparql update requests
                api(project(":rdf:dsl"))
                // methods such as `bodyAsBindings` expose the sparql types
                api(project(":sparql:common"))
                // providing custom content negotiation support
                // as an `implementation` instead of `api` so it's not exposing all there is to ktor
                implementation(libs.ktor.client.contentNegotiation)
                // used to actually (de)serialize the bindings representation from client responses
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
