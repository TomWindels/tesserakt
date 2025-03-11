plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":sparql"))
                implementation(project(":sparql:core"))
                implementation(project(":sparql:debugging"))
                implementation(project(":sparql:compiler"))
                implementation(project(":sparql:runtime"))
            }
        }
    }
}
