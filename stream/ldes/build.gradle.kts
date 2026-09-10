plugins {
    id("kmp-package")
}

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":rdf"))
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

                implementation(project(":utils"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":utils"))
                implementation(project(":serialization:trig"))
                implementation(project(":rdf:dsl"))
            }
        }
    }
}
