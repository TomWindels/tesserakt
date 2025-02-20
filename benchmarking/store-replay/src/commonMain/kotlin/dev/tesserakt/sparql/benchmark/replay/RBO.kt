package dev.tesserakt.sparql.benchmark.replay

import dev.tesserakt.rdf.ontology.Ontology

internal object RBO: Ontology {

    override val prefix: String = "rbo"
    override val base_uri: String = "http://example.org/replayBenchmark/"

    val ReplayBenchmark = this("ReplayBenchmark")
    val usesQuery = this("usesQuery")
    val usesDataset = this("usesDataset")

}
