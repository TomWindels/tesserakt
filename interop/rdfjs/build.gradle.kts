plugins {
    id("js-package")
}

kotlin {
    sourceSets {
        getByName("jsMain") {
            dependencies {
                api(project(":rdf"))
                implementation(npm("n3", "1.17.3"))
            }
        }
    }
}
