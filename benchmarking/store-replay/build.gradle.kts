import com.android.build.gradle.internal.tasks.factory.dependsOn

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

tasks.named("jsNodeProductionLibraryDistribution").dependsOn("jsTestTestDevelopmentExecutableCompileSync")
tasks.named("jsNodeTest").dependsOn("jsProductionLibraryCompileSync")
