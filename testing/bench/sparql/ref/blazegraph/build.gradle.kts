plugins {
    id("base-config")
}

group = "sparql.bench"

kotlin {
    jvm()
    // reports say this has better performance for BlazeGraph
    jvmToolchain(9)

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":testing:bench:sparql:core"))
                implementation("com.blazegraph:bigdata-core:2.1.4")
            }
        }
    }
}
