import java.util.*

apply(from = "./buildSrc/settings.gradle.kts")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

/* helpers (see below) */

fun Settings.local(name: String): String? =
    runCatching {
        val properties = Properties()
        properties.load(File(rootDir.absolutePath + "/local.properties").inputStream())
        return properties.getProperty(name, null)
    }.getOrNull()

fun Settings.hasEnabled(name: String): Boolean {
    val value = local(name)?.lowercase() ?: return false
    return value in setOf("true", "enabled")
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
include("sparql:validator")

include("serialization:core")
include("serialization:common")
include("serialization:n-triples")
include("serialization:turtle")
include("serialization:trig")
include("serialization:n3")

include("stream:ldes")

include("interop:jena")
include("interop:rdfjs")

/* internal modules (distributed) */

include("utils")

/* internal modules (not distributed) */

include("testing:bench:microbench")

include("testing:bench:sparql")
include("testing:bench:sparql:core")

if (hasEnabled("bench.sparql.blazegraph")) {
    include("testing:bench:sparql:ref:blazegraph")
}
if (hasEnabled("bench.sparql.jena")) {
    include("testing:bench:sparql:ref:jena")
}
if (hasEnabled("bench.sparql.rdfox")) {
    include("testing:bench:sparql:ref:rdfox")
}
if (hasEnabled("bench.sparql.comunica")) {
    include("testing:bench:sparql:ref:comunica")
}

include("testing:tooling:environment")
include("testing:tooling:replay-benchmark")

include("testing:rdf-test-suite-js")

include("js-build")
