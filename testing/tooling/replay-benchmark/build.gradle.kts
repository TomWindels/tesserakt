plugins {
    id("kmp-package")
}

group = "testing"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":rdf:snapshot-store"))
                implementation(project(":rdf:dsl"))
                implementation(project(":serialization:trig"))
            }
        }
        getByName("jsMain") {
            dependencies {
                api(project(":interop:rdfjs"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":utils"))
                // we require the ontologies during the encoding test
                implementation(project(":stream:ldes"))
            }
        }
    }
}
