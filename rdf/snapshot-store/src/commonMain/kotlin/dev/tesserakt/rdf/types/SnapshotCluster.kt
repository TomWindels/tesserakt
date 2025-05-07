package dev.tesserakt.rdf.types

interface SnapshotCluster {

    val identifier: Quad.NamedTerm

    fun extractClusterContent(source: Store): Store

}
