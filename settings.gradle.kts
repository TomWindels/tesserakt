apply(from = "./buildSrc/settings.gradle.kts")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "tesserakt"

/* public facing modules */

include("rdf")
include("rdf:dsl")
include("rdf:snapshot-store")

include("n3")
include("n3:dsl")

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

/* internal modules (distributed) */

include("utils")

/* internal modules (not distributed) */

include("testing:rdf-test-suite-js")
include("testing:tooling:environment")
include("testing:tooling:replay-benchmark")

include("benchmarking")
include("benchmarking:microbench")
include("benchmarking:runner")
include("benchmarking:runner:core")
include("benchmarking:runner:ref:blazegraph")
include("benchmarking:runner:ref:comunica")
include("benchmarking:runner:ref:jena")
include("benchmarking:runner:ref:rdfox")

include("js-build")
