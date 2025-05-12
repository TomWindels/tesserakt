plugins {
    // not distributed as a package
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
                implementation(project(":serialization:core"))
                api(project(":serialization:common"))
                api(project(":rdf"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":rdf:dsl"))
                implementation(project(":testing:tooling:environment"))
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":utils"))
                implementation(project(":interop:jena"))
                implementation(project(":testing:tooling:environment"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
    }
}
