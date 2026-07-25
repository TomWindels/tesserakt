plugins {
    id("jvm-package")
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":rdf"))
                // contains DatasetFactory
                api(libs.apache.jena)
            }
        }
    }
}

java {
    // jena requires Java 17 and up
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
