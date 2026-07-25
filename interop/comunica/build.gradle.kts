// we're not being exported as a package ootb
plugins {
    id("base-config")
}

kotlin {
    js {
        nodejs()
        generateTypeScriptDefinitions()
        binaries.library()
        // makes `import { ComunicaApiEngine } from "package"` possible
        useEsModules()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                api(project(":interop:rdfjs"))

                implementation(project(":rdf"))
                implementation(project(":sparql"))
                implementation(project(":utils"))

                implementation(npm("rdf-data-factory", "^2.0.0"))
                implementation(npm("@comunica/utils-bindings-factory", "^5.0.0"))
            }
        }
    }
}
