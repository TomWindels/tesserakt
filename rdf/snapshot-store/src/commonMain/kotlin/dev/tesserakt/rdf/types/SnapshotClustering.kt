package dev.tesserakt.rdf.types

interface SnapshotClustering {

    fun divide(snapshots: List<Set<Quad>>): Set<SnapshotCluster>

}
