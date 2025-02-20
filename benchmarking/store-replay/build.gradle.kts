plugins {
    id("package-conventions")
}

kotlin {
    js {
        generateTypeScriptDefinitions()
        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":rdf"))
                implementation(project(":rdf-dsl"))
                implementation(project(":serialization:trig"))
                implementation(project(":stream:ldes"))
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
            }
        }
    }
}
