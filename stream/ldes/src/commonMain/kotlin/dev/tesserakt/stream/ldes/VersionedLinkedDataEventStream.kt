package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.impl.AbstractStore
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.util.mapTo
import dev.tesserakt.util.single
import dev.tesserakt.util.singleOrNull

class VersionedLinkedDataEventStream<StreamElement>(
    val identifier: Quad.NamedTerm,
    data: Store,
    private val comparator: Comparator<Quad.Literal> = DateComparator,
    private val transform: StreamTransform<StreamElement>,
): AbstractStore() {

    data class Member(
        /**
         * The corresponding version identifier, i.e. `#post1v0`
         */
        val identifier: Quad.NamedTerm,
        /**
         * The base version identifier
         */
        val base: Quad.NamedTerm,
        /**
         * This version's timestamp value
         */
        val timestampValue: Quad.Literal
    )

    private val store = MutableStore(data)

    private val timestampPath = store.iter(s = identifier, p = LDES.timestampPath).singleOrNull()?.o
        as? Quad.NamedTerm ?: streamFormatError("Expected exactly one `timestampPath`!")

    private val versionOfPath = store.iter(s = identifier, p = LDES.versionOfPath).singleOrNull()?.o
        as? Quad.NamedTerm ?: streamFormatError("Expected exactly one `versionOfPath`!")

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

    val members: List<Member> get() = _members

    /**
     * All various (distinct) [timestampPath] values of the individual members, sorted according to the used comparator
     *  implementation.
     */
    val timestamps: List<Quad.Literal>
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

    fun read(until: Quad.Literal): Store = transform.decode(
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
    fun read(base: Quad.NamedTerm, timestampValue: Quad.Literal, inclusive: Boolean = true): StreamElement? {
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
        timestamp: Quad.Literal,
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
            comparator: Comparator<Quad.Literal> = DateComparator
        ): VersionedLinkedDataEventStream<StreamUnit> = VersionedLinkedDataEventStream(
            identifier = identifier,
            transform = transform,
            comparator = comparator,
            data = MutableStore()
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
            comparator: Comparator<Quad.Literal> = DateComparator
        ): VersionedLinkedDataEventStream<StreamUnit> = VersionedLinkedDataEventStream(
            identifier = identifier,
            data = store,
            comparator = comparator,
            transform = transform,
        )

    }

    /* build up methods */

    private fun materializeVersionedMembers(store: Store): MutableList<Member> =
        store
            .iter(s = identifier, p = LDES.member)
            .mapTo(mutableListOf()) {
                val identifier = it.o as? Quad.NamedTerm
                    ?: streamFormatError("Member $identifier is not an IRI")
                materialize(store, identifier)
            }

    private fun materialize(
        store: Store,
        identifier: Quad.NamedTerm,
    ): Member {
        return Member(
            identifier = identifier,
            base = store.iter(s = identifier, p = versionOfPath).singleOrNull()?.o as? Quad.NamedTerm
                ?: streamFormatError("Member $identifier has an incorrect amount of triples with predicate $versionOfPath associated, or is not an IRI"),
            timestampValue = store.iter(s = identifier, p = timestampPath).singleOrNull()?.o as? Quad.Literal
                ?: streamFormatError("Member $identifier has an incorrect amount of triples with predicate $timestampPath associated, or is not a literal term"),
        )
    }

    private fun streamFormatError(message: String): Nothing =
        throw InvalidStreamFormatException(identifier, message)

}

private class InvalidStreamFormatException(
    identifier: Quad.NamedTerm,
    message: String
): RuntimeException("Stream $identifier is not correctly formatted! $message")
