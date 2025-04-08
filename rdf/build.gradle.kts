plugins {
    id("package-conventions")
}

group = "rdf"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
            }
        }
    }
}
