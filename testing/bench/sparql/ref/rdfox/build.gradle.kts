plugins {
    id("base-config")
}

group = "sparql.bench"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":testing:bench:sparql:core"))
                implementation(files("libs/JRDFox.jar"))
                implementation(project(":serialization:trig"))
            }
        }
    }
}
