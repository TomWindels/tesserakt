package dev.tesserakt.rdf.types

import kotlin.jvm.JvmInline

object NaiveSnapshotClustering: SnapshotClustering {

    @JvmInline
    value class SubjectCluster(override val identifier: Quad.NamedTerm): SnapshotCluster {
        override fun extractClusterContent(source: Store): Store {
            return source.filterTo(mutableSetOf()) { it.s == identifier }.toStore()
        }
    }

    override fun divide(snapshots: List<Store>): Set<SubjectCluster> =
        snapshots.flatMapTo(mutableSetOf()) { quads ->
            quads.mapTo(mutableSetOf()) { quad ->
                SubjectCluster(
                    identifier = quad.s as? Quad.NamedTerm ?:
                        throw IllegalArgumentException("Naive snapshot clustering does not support blank nodes!")
                )
            }
        }

}
