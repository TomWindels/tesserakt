plugins {
    kotlin("jvm")
}

group = "sparql"

kotlin {
    dependencies {
        implementation(kotlin("test"))
        implementation(project(":utils"))
        implementation(project(":sparql"))
        implementation(project(":sparql:core"))
        implementation(project(":sparql:debugging"))
        implementation(project(":sparql:compiler"))
        implementation(project(":sparql:runtime"))
        implementation(project(":testing:tooling:environment"))
        implementation(project(":rdf:dsl"))
        implementation(project(":serialization:turtle"))
    }
}
