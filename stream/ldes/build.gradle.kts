plugins {
    id("kmp-package")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":rdf"))
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":serialization:trig"))
                implementation(project(":rdf:dsl"))
            }
        }
    }
}
