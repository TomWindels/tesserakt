plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":testing:suite"))
                implementation(project(":rdf-dsl"))
                implementation(project(":serialization"))
                implementation(project(":extra"))
            }
        }
    }
}
