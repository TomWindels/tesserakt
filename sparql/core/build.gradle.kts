plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":rdf"))
                api(project(":sparql:common"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
