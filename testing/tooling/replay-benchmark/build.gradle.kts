plugins {
    id("kmp-package")
}

group = "testing"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf:snapshot-store"))
                implementation(project(":rdf:dsl"))
                implementation(project(":serialization:trig"))
            }
        }
        val jsMain by getting {
            dependencies {
                api(project(":interop:rdfjs"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":utils"))
                // we require the ontologies during the encoding test
                implementation(project(":stream:ldes"))
            }
        }
    }
}
