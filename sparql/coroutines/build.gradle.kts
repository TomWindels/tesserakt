plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":sparql"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                implementation(project(":utils"))
                implementation(project(":sparql:runtime"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":rdf:dsl"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
    }
}
