package dev.tesserakt.rdf.types

interface SnapshotClustering {

    fun divide(snapshots: List<Store>): Set<SnapshotCluster>

}
