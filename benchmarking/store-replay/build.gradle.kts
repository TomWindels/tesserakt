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
                api(project(":rdf"))
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

tasks.named("jsNodeProductionLibraryDistribution").dependsOn("jsTestTestDevelopmentExecutableCompileSync")
tasks.named("jsNodeTest").dependsOn("jsProductionLibraryCompileSync")
