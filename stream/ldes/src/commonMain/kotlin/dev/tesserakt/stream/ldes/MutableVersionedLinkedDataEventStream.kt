package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.IndexedStore
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.util.single

class MutableVersionedLinkedDataEventStream<StreamElement>(
    identifier: Quad.NamedTerm,
    private val store: MutableStore,
    internal val comparator: Comparator<Quad.TypedLiteral> = DateComparator,
    internal val transform: StreamTransform<StreamElement>,
): VersionedLinkedDataEventStream<StreamElement>(identifier, store) {

    private val _members = materializeVersionedMembers(IndexedStore(store)) // temporary index for initial set of members
        .also { members ->
            if (members.isEmpty()) {
                return@also
            }
            val type = members.first().timestampValue.type
            if (members.any { it.timestampValue.type != type }) {
                streamFormatError("Inconsistent timestamp value types detected. Used timestamp values are ${members.mapTo(mutableSetOf()) { it.timestampValue.type }.joinToString()}")
            }
        }

    override val members: List<Member> get() = _members

    /**
     * All various (distinct) [timestampPath] values of the individual members, sorted according to the used comparator
     *  implementation.
     */
    override val timestamps: List<Quad.TypedLiteral>
        get() = _members
            .mapTo(mutableSetOf()) { it.timestampValue }
            .sortedWith(comparator)

    init {
        if (!store.iter(s = identifier, p = RDF.type, o = LDES.EventStream).hasNext()) {
            streamFormatError("Stream $identifier does not have the event stream type set!")
        }
    }

    /* public api */

    override val size: Int get() = store.size

    override fun isEmpty(): Boolean = store.isEmpty()

    override fun iterator(): Iterator<Quad> = store.iterator()

    override fun read(until: Quad.TypedLiteral): Store = transform.decode(
        source = store,
        identifiers = _members
            // only allowing members that have been added before (including) the provided parameter
            .filter { comparator.compare(it.timestampValue, until) <= 0 }
            // taking the most recent ones since only; order affects which variants of the base versions are kept
            .sortedWith(compareByDescending(comparator) { it.timestampValue })
            .distinctBy { it.base }
            .mapTo(mutableSetOf()) { it.identifier }
    )

    /**
     * Read a specific version of a member (identified using [base]) at a given point in time (according
     *  to [timestampValue]). The additional [inclusive] flag dictates whether versions with a [timestampValue]
     *  identical to the one provided are allowed.
     */
    override fun read(base: Quad.NamedTerm, timestampValue: Quad.TypedLiteral, inclusive: Boolean): StreamElement? {
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

    fun add(
        baseVersion: Quad.NamedTerm,
        timestamp: Quad.TypedLiteral,
        data: StreamElement,
    ) {
        // it's discouraged to use a `#`, as most serialization formats cannot use a prefixed representation of the
        //  member's name anymore (it would denote the start of a comment)
        val hint = Quad.NamedTerm("${baseVersion.value}_v${_members.count { it.base == baseVersion }}")
        val element = transform.encode(target = store, element = data, hint = hint)
        store.add(Quad(identifier, LDES.member, element))
        store.add(Quad(element, timestampPath, timestamp))
        store.add(Quad(element, versionOfPath, baseVersion))
        _members.add(
            Member(
                identifier = element,
                base = baseVersion,
                timestampValue = timestamp,
            )
        )
    }

    companion object {

        fun <StreamUnit> initialise(
            identifier: Quad.NamedTerm,
            timestampPath: Quad.NamedTerm = DC.modified,
            versionOfPath: Quad.NamedTerm = DC.isVersionOf,
            transform: StreamTransform<StreamUnit>,
            comparator: Comparator<Quad.TypedLiteral> = DateComparator
        ): MutableVersionedLinkedDataEventStream<StreamUnit> = MutableVersionedLinkedDataEventStream(
            identifier = identifier,
            transform = transform,
            comparator = comparator,
            store = MutableStore()
                .apply {
                    // minimum set of triples required for a valid versioned LDES with the provided arguments
                    add(Quad(identifier, RDF.type, LDES.EventStream))
                    add(Quad(identifier, LDES.timestampPath, timestampPath))
                    add(Quad(identifier, LDES.versionOfPath, versionOfPath))
                }
        )

        fun <StreamUnit> from(
            store: Store,
            transform: StreamTransform<StreamUnit>,
            identifier: Quad.NamedTerm =
                store.iter(p = RDF.type, o = LDES.EventStream).single().s as Quad.NamedTerm,
            comparator: Comparator<Quad.TypedLiteral> = DateComparator
        ): MutableVersionedLinkedDataEventStream<StreamUnit> = MutableVersionedLinkedDataEventStream(
            identifier = identifier,
            store = MutableStore(store),
            comparator = comparator,
            transform = transform,
        )

    }

}
