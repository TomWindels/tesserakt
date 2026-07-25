package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.*
import dev.tesserakt.rdf.types.factory.IndexedStore
import dev.tesserakt.rdf.types.factory.indexedStoreOf
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.util.single

class IndexedVersionedLinkedDataEventStream<StreamElement>(
    identifier: Quad.NamedTerm,
    private val store: IndexedStore,
    private val comparator: Comparator<Quad.TypedLiteral> = DateComparator,
    private val transform: StreamTransform<StreamElement>,
): VersionedLinkedDataEventStream<StreamElement>(identifier, store) {

    private val members = materializeVersionedMembers(store)
        .groupBy { member ->
            member.base
        }
        .mapValues { (_, versions) ->
            versions.associateBy { it.timestampValue }
        }

    /**
     * All various (distinct) [timestampPath] values of the individual members, sorted according to the used comparator
     *  implementation.
     */
    override val timestamps: Collection<Quad.TypedLiteral> by lazy {
        members
            .flatMapTo(mutableSetOf()) { it.value.keys }
            .sortedWith(compareBy(comparator) { key -> key })
    }

    init {
        if (!store.iter(s = identifier, p = RDF.type, o = LDES.EventStream).hasNext()) {
            streamFormatError("Stream $identifier does not have the event stream type set!")
        }
    }

    /* public api */

    override val size: Int get() = store.size

    fun membersWithVersionOnTimestamp(timestampValue: Quad.TypedLiteral, target: MutableSet<Member> = mutableSetOf()): Set<Member> {
        return members.values.flatMapTo(target) { if (timestampValue in it) it.values else emptyList() }
    }

    override fun isEmpty(): Boolean = store.isEmpty()

    override val context: EncodingContext
        get() = store.context

    override fun encodedIterator(): Iterator<EncodedQuad> {
        return store.encodedIterator()
    }

    override fun encodedIter(
        s: Quad.Subject?,
        p: Quad.Predicate?,
        o: Quad.Object?,
        g: Quad.Graph?
    ): Iterator<EncodedQuad> {
        return store.encodedIter(s, p, o, g)
    }

    override fun read(until: Quad.TypedLiteral): Store = transform.decode(
        source = store,
        identifiers = members
            // because of distinct member values, only selecting one of the values collection below makes it
            //  automatically distinct
            .values
            // we only care for the most recent timestamp entry satisfying our requirement (`<= 0`)
            .mapNotNull { map ->
                map
                    .filterKeys { timestamp -> comparator.compare(timestamp, until) <= 0 }
                    .maxWithOrNull(compareBy(comparator) { (key, _) -> key })
            }
            .mapTo(mutableSetOf()) { (_, member) -> member.identifier }
    )

    /**
     * Read a specific version of a member (identified using [base]) at a given point in time (according
     *  to [timestampValue]). The additional [inclusive] flag dictates whether versions with a [timestampValue]
     *  identical to the one provided are allowed.
     */
    override fun read(base: Quad.NamedTerm, timestampValue: Quad.TypedLiteral, inclusive: Boolean): StreamElement? {
        val entries = members[base] ?: return null
        val entry = entries
            .filterKeys { timestamp ->
                val comparison = comparator.compare(timestamp, timestampValue)
                comparison < 0 || inclusive && comparison == 0
            }
            .maxWithOrNull(compareBy(comparator) { (key, _) -> key })
            ?: return null
        return transform.decode(source = store, identifier = entry.value.identifier)
    }

    companion object {

        fun <StreamUnit> initialise(
            identifier: Quad.NamedTerm,
            timestampPath: Quad.NamedTerm = DC.modified,
            versionOfPath: Quad.NamedTerm = DC.isVersionOf,
            transform: StreamTransform<StreamUnit>,
            comparator: Comparator<Quad.TypedLiteral> = DateComparator
        ): IndexedVersionedLinkedDataEventStream<StreamUnit> = IndexedVersionedLinkedDataEventStream(
            identifier = identifier,
            transform = transform,
            comparator = comparator,
            store = indexedStoreOf(
                // minimum set of triples required for a valid versioned LDES with the provided arguments
                Quad(identifier, RDF.type, LDES.EventStream),
                Quad(identifier, LDES.timestampPath, timestampPath),
                Quad(identifier, LDES.versionOfPath, versionOfPath),
            )
        )

        fun <StreamUnit> from(
            store: Store,
            transform: StreamTransform<StreamUnit>,
            identifier: Quad.NamedTerm =
                store.iter(p = RDF.type, o = LDES.EventStream).single().s as Quad.NamedTerm,
            comparator: Comparator<Quad.TypedLiteral> = DateComparator
        ): IndexedVersionedLinkedDataEventStream<StreamUnit> = IndexedVersionedLinkedDataEventStream(
            identifier = identifier,
            store = IndexedStore(store),
            comparator = comparator,
            transform = transform,
        )

    }

}
