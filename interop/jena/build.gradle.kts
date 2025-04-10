plugins {
    id("kmp-package")
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":rdf"))
                // contains DatasetFactory
                api("org.apache.jena:jena-arq:5.0.0")
            }
        }
    }
}
