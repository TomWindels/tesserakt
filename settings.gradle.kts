apply(from = "./buildSrc/settings.gradle.kts")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "tesserakt"
include("rdf")
include("extra")
include("rdf-dsl")
include("rdfjs")
include("sparql")
include("serialization")
include("common")
include("tests")
