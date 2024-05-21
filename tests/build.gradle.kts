plugins {
    // not following any conventions; not published or anything; only testing on the JVM & JS targets
    kotlin("multiplatform")
}

kotlin {
    jvm {
        jvmToolchain(8)
    }
    js {
        nodejs()
    }
    sourceSets {
        // core modules tested by all test targets
        val commonTest by getting {
            dependencies {
                implementation(project(":common"))
                implementation(project(":extra"))
                implementation(project(":rdf"))
                implementation(project(":rdf-dsl"))
                implementation(project(":sparql"))

                implementation(kotlin("test"))
            }
        }
    }
}
