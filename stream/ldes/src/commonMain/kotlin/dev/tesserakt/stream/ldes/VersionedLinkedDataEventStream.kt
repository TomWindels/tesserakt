package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES

class VersionedLinkedDataEventStream<StreamElement>(
    val identifier: Quad.NamedTerm,
    private val store: Store,
    private val comparator: Comparator<Quad.Literal> = DateComparator,
    private val transform: StreamTransform<StreamElement>,
): Set<Quad> by store {

    private data class VersionedMember(
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

    private val timestampPath = store.singleOrNull { it.s == identifier && it.p == LDES.timestampPath }?.o
        as? Quad.NamedTerm ?: streamFormatError("Expected exactly one `timestampPath`!")

    private val versionOfPath = store.singleOrNull { it.s == identifier && it.p == LDES.versionOfPath }?.o
        as? Quad.NamedTerm ?: streamFormatError("Expected exactly one `versionOfPath`!")

    private val members = materializeVersionedMembers(store)
        .also { members ->
            if (members.isEmpty()) {
                return@also
            }
            val type = members.first().timestampValue.type
            if (members.any { it.timestampValue.type != type }) {
                streamFormatError("Inconsistent timestamp value types detected. Used timestamp values are ${members.mapTo(mutableSetOf()) { it.timestampValue.type }.joinToString()}")
            }
        }

    /**
     * All various (distinct) [timestampPath] values of the individual members, sorted according to the used comparator
     *  implementation.
     */
    val timestamps: List<Quad.Literal>
        get() = members
            .mapTo(mutableSetOf()) { it.timestampValue }
            .sortedWith(comparator)

    init {
        if (store.none { it.s == identifier && it.p == RDF.type && it.o == LDES.EventStream }) {
            streamFormatError("Stream $identifier has no ")
        }
    }

    /* public api */

    fun read(until: Quad.Literal): Store = transform.decode(
        source = store,
        identifiers = members
            // only allowing members that have been added before (including) the provided parameter
            .filter { comparator.compare(it.timestampValue, until) <= 0 }
            // taking the most recent ones since only; order affects which variants of the base versions are kept
            .sortedWith(compareByDescending(comparator) { it.timestampValue })
            .distinctBy { it.base }
            .mapTo(mutableSetOf()) { it.identifier }
    )

    fun add(
        baseVersion: Quad.NamedTerm,
        timestamp: Quad.Literal,
        data: StreamElement,
    ) {
        val hint = Quad.NamedTerm("${baseVersion.value}#v${members.count { it.base == baseVersion }}")
        val element = transform.encode(target = store, element = data, hint = hint)
        store.add(Quad(identifier, LDES.member, element))
        store.add(Quad(element, timestampPath, timestamp))
        store.add(Quad(element, versionOfPath, baseVersion))
        members.add(
            VersionedMember(
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
            store = Store()
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
                store.single { it.p == RDF.type && it.o == LDES.EventStream }.s as Quad.NamedTerm,
            comparator: Comparator<Quad.Literal> = DateComparator
        ): VersionedLinkedDataEventStream<StreamUnit> = VersionedLinkedDataEventStream(
            identifier = identifier,
            store = store,
            comparator = comparator,
            transform = transform,
        )

    }

    /* build up methods */

    private fun materializeVersionedMembers(store: Store): MutableList<VersionedMember> =
        store
            .filter { it.s == identifier && it.p == LDES.member }
            .mapTo(mutableListOf()) {
                val identifier = it.o as? Quad.NamedTerm
                    ?: streamFormatError("Member $identifier is not an IRI")
                materialize(store, identifier)
            }

    private fun materialize(
        store: Store,
        identifier: Quad.NamedTerm,
    ): VersionedMember {
        return VersionedMember(
            identifier = identifier,
            base = store.singleOrNull { it.s == identifier && it.p == versionOfPath }?.o as? Quad.NamedTerm
                ?: streamFormatError("Member $identifier has an incorrect amount of triples with predicate $versionOfPath associated, or is not an IRI"),
            timestampValue = store.singleOrNull { it.s == identifier && it.p == timestampPath }?.o as? Quad.Literal
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
