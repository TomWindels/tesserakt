plugins {
    // only JS is used, new way of doing things
    // see https://kotlinlang.org/docs/multiplatform-compatibility-guide.html#96e300af
    kotlin("multiplatform")
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
