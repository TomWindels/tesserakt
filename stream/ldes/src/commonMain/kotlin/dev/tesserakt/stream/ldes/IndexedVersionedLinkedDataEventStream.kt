package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.IndexedStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.IndexedStore
import dev.tesserakt.rdf.types.factory.indexedStoreOf
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.util.single

class IndexedVersionedLinkedDataEventStream<StreamElement>(
    identifier: Quad.NamedTerm,
    private val store: IndexedStore,
    private val comparator: Comparator<Quad.Literal> = DateComparator,
    private val transform: StreamTransform<StreamElement>,
): VersionedLinkedDataEventStream<StreamElement>(identifier, store) {

    private val _members = materializeVersionedMembers(store)
        .also { members ->
            if (members.isEmpty()) {
                return@also
            }
            val type = members.first().timestampValue.type
            if (members.any { it.timestampValue.type != type }) {
                streamFormatError("Inconsistent timestamp value types detected. Used timestamp values are ${members.mapTo(mutableSetOf()) { it.timestampValue.type }.joinToString()}")
            }
        }
        .sortedWith(compareBy(comparator) { it.timestampValue })

    override val members: List<Member> get() = _members

    /**
     * All various (distinct) [timestampPath] values of the individual members, sorted according to the used comparator
     *  implementation.
     */
    override val timestamps: List<Quad.Literal> by lazy {
        _members
            .map { it.timestampValue }
            .distinct()
    }

    init {
        if (!store.iter(s = identifier, p = RDF.type, o = LDES.EventStream).hasNext()) {
            streamFormatError("Stream $identifier does not have the event stream type set!")
        }
    }

    /* public api */

    override val size: Int get() = store.size

    override fun isEmpty(): Boolean = store.isEmpty()

    override fun iterator(): Iterator<Quad> = store.iterator()

    override fun read(until: Quad.Literal): Store = transform.decode(
        source = store,
        identifiers = _members
            // only allowing members that have been added before (including) the provided parameter;
            //  we can use takeWhile as the `_members` collection is sorted
            .takeWhile { comparator.compare(it.timestampValue, until) <= 0 }
            // taking the most recent ones since only; order affects which variants of the base versions are kept;
            //  as the newest ones are in the back, we have to reverse it before getting the distinct names
            .asReversed()
            .distinctBy { it.base }
            .mapTo(mutableSetOf()) { it.identifier }
    )

    /**
     * Read a specific version of a member (identified using [base]) at a given point in time (according
     *  to [timestampValue]). The additional [inclusive] flag dictates whether versions with a [timestampValue]
     *  identical to the one provided are allowed.
     */
    override fun read(base: Quad.NamedTerm, timestampValue: Quad.Literal, inclusive: Boolean): StreamElement? {
        val version = _members
            .filter {
                if (it.base != base)
                    return@filter false
                val comparison = comparator.compare(it.timestampValue, timestampValue)
                comparison < 0 || inclusive && comparison == 0
            }.maxWithOrNull(compareBy(comparator) { it.timestampValue })
            ?: return null
        return transform.decode(source = store, identifier = version.identifier)
    }

    companion object {

        fun <StreamUnit> initialise(
            identifier: Quad.NamedTerm,
            timestampPath: Quad.NamedTerm = DC.modified,
            versionOfPath: Quad.NamedTerm = DC.isVersionOf,
            transform: StreamTransform<StreamUnit>,
            comparator: Comparator<Quad.Literal> = DateComparator
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
            comparator: Comparator<Quad.Literal> = DateComparator
        ): IndexedVersionedLinkedDataEventStream<StreamUnit> = IndexedVersionedLinkedDataEventStream(
            identifier = identifier,
            store = IndexedStore(store),
            comparator = comparator,
            transform = transform,
        )

    }

}
