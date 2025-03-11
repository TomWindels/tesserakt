plugins {
    id("package-conventions")
}

group = "sparql"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                api(project(":rdf"))
            }
        }
    }
}
