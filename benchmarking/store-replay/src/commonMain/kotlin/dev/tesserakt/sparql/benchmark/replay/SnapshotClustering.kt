package dev.tesserakt.sparql.benchmark.replay

import dev.tesserakt.rdf.types.Quad

interface SnapshotClustering {

    fun divide(snapshots: List<Set<Quad>>): Set<SnapshotCluster>

}
