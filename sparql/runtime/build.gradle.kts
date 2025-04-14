plugins {
    id("kmp-package")
}

group = "sparql"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
                api(project(":sparql:core"))
                api(project(":sparql:common"))
            }
        }
    }
}
