apply(from = "./buildSrc/settings.gradle.kts")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "tesserakt"
include("common")
include("extra")

include("rdf")
include("rdf-dsl")

include("n3")
include("n3-dsl")

include("sparql")

include("serialization:core")
include("serialization:n-triples")
include("serialization:turtle")
include("serialization:trig")
include("serialization:n3")
include("serialization")

include("interop:jena")
include("interop:rdfjs")

include("testing:suite")
include("testing:rdf-test-suite-js")

include("benchmarking")
