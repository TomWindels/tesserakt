plugins {
    id("js-package")
}

kotlin {
    js {
        browser()
        nodejs()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                api(project(":rdf"))
                implementation(npm("n3", "1.17.3"))
            }
        }
    }
}
