package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.stream.ldes.StreamTransform
import dev.tesserakt.stream.ldes.VersionedLinkedDataEventStream
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class SnapshotStore private constructor(
    private val stream: VersionedLinkedDataEventStream<Store>
) {

    data class Diff(
        val insertions: Set<Quad>,
        val deletions: Set<Quad>
    ) {

        override fun toString() = buildString {
            var iter = insertions.iterator()
            if (iter.hasNext()) {
                append("[+] ${iter.next()}")
            }
            while (iter.hasNext()) {
                append('\n')
                append("[+] ${iter.next()}")
            }
            if (insertions.isNotEmpty() && deletions.isNotEmpty()) {
                append('\n')
            }
            iter = deletions.iterator()
            if (iter.hasNext()) {
                append("[-] ${iter.next()}")
            }
            while (iter.hasNext()) {
                append('\n')
                append("[-] ${iter.next()}")
            }
        }

        companion object {

            // TODO: a more robust comparison algorithm, properly tracking changes in blank nodes, for a more
            //  accurate diff representation
            fun between(first: Set<Quad>, second: Set<Quad>) = Diff(
                insertions = second - first,
                deletions = first - second
            )

        }

    }

    class Builder(
        start: Store = EmptyStore,
        private val clustering: SnapshotClustering = NaiveSnapshotClustering
    ) {

        private val snapshots = mutableListOf(start)

        fun addSnapshot(store: Store): Builder {
            if (snapshots.last() != store) {
                snapshots.add(store)
            }
            return this
        }

        fun build(identifier: Quad.NamedTerm): SnapshotStore {
            // constructing the base LDES, with no base data inserted
            val stream = VersionedLinkedDataEventStream.initialise(
                identifier = identifier,
                transform = StreamTransform.GraphBased
            )
            // creating all clusters, making up the individual base versions
            val clusters = clustering.divide(snapshots)
            // creating all final snapshots as member groups, all with identical version timestamp, and cluster version
            //  tag (starting from nearest minute)
            val date = Instant.fromEpochSeconds(Clock.System.now().epochSeconds / 60 * 60)
            // keeping track of the previous version; as none are initially encoded, the very first version is empty
            var previous = emptyMap<SnapshotCluster, Store>()
            // now encoding all cluster changes
            snapshots.forEachIndexed { i, store ->
                val timestamp = Quad.Literal(
                    value = date.plus(i.minutes).toString(),
                    type = XSD.date
                )
                val current = clusters.associateWith { cluster -> cluster.extractClusterContent(store) }
                // all cluster versions worth updating are those that have different values compared to the previous iteration
                val remaining = current.filter { it.value != previous[it.key] }
                // writing down all remaining clusters
                remaining.forEach { (cluster, content) ->
                    stream.add(
                        baseVersion = cluster.identifier,
                        timestamp = timestamp,
                        data = content
                    )
                }
                previous = current
            }
            return SnapshotStore(stream = stream)
        }

    }

    constructor(store: Store): this(VersionedLinkedDataEventStream.from(store, transform = StreamTransform.GraphBased))

    val identifier: Quad.NamedTerm get() = stream.identifier

    val snapshotCount: Int
        get() = stream.timestamps.size

    val snapshots = object: Iterable<Store> {
        override fun iterator(): Iterator<Store> = iterator {
            stream.timestamps.forEach { threshold ->
                yield(stream.read(threshold))
            }
        }
    }

    val diffs = object: Iterable<Diff> {
        override fun iterator(): Iterator<Diff> = iterator {
            val timestamps = stream.timestamps.iterator()
            val members = mutableSetOf<Quad.NamedTerm>()
            val old = mutableSetOf<Quad>()
            val new = mutableSetOf<Quad>()
            while (timestamps.hasNext()) {
                val timestamp = timestamps.next()
                // resetting the previous iteration (members affected before != members affected now)
                members.clear()
                old.clear()
                new.clear()
                // getting all members affected by this increment in timestampValue
                stream.members
                    .mapNotNullTo(members) { member -> member.base.takeIf { member.timestampValue == timestamp } }
                // it shouldn't be possible for the
                require(members.isNotEmpty())
                // collecting all original versions
                members.flatMapTo(old) { base ->
                    stream.read(base = base, timestampValue = timestamp, inclusive = false)?.toSet() ?: emptySet()
                }
                // collecting all updated versions
                members.flatMapTo(new) { base ->
                    stream.read(base = base, timestampValue = timestamp, inclusive = true)?.toSet() ?: emptySet()
                }
                // calculating & yielding the combined diff
                yield(Diff.between(old, new))
            }
        }
    }

    fun toStore(): Store {
        return stream.toStore()
    }

}
