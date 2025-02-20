package dev.tesserakt.sparql.benchmark.replay

import dev.tesserakt.rdf.types.Quad

interface SnapshotCluster {

    val identifier: Quad.NamedTerm

    fun extractClusterContent(source: Set<Quad>): Set<Quad>

}
