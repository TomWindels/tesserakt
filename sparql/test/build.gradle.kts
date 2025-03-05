plugins {
    kotlin("jvm")
}

kotlin {
    dependencies {
        implementation(kotlin("test"))
        implementation(project(":sparql"))
        implementation(project(":extra"))
        implementation(project(":testing:suite"))
        implementation(project(":rdf-dsl"))
        implementation(project(":serialization"))
    }
}
