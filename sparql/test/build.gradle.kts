plugins {
    kotlin("jvm")
}

group = "sparql"

kotlin {
    dependencies {
        implementation(kotlin("test"))
        implementation(project(":sparql"))
        implementation(project(":sparql:core"))
        implementation(project(":sparql:debugging"))
        implementation(project(":sparql:compiler"))
        implementation(project(":sparql:runtime"))
        implementation(project(":testing:suite"))
        implementation(project(":rdf-dsl"))
        implementation(project(":serialization"))
    }
}
