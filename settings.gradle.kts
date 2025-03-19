apply(from = "./buildSrc/settings.gradle.kts")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "tesserakt"
include("common")

include("rdf")
include("rdf-dsl")

include("n3")
include("n3-dsl")

include("sparql")
include("sparql:core")
include("sparql:common")
include("sparql:debugging")
include("sparql:compiler")
include("sparql:runtime")
include("sparql:test")

include("serialization:core")
include("serialization:common")
include("serialization:n-triples")
include("serialization:turtle")
include("serialization:trig")
include("serialization:n3")
include("serialization")

include("stream:ldes")

include("interop:jena")
include("interop:rdfjs")

include("testing:suite")
include("testing:rdf-test-suite-js")

include("benchmarking")
include("benchmarking:store-replay")
include("benchmarking:runner")

include("js-build")
