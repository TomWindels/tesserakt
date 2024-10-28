plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":interop:jena"))
                implementation(project(":tests"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
    }
}
