package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.impl.AbstractStore
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.util.mapTo
import dev.tesserakt.util.singleOrNull

abstract class VersionedLinkedDataEventStream<StreamElement>(
    val identifier: Quad.NamedTerm,
    store: Store,
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

    protected val timestampPath = store.iter(s = identifier, p = LDES.timestampPath).singleOrNull()?.o
        as? Quad.NamedTerm ?: streamFormatError("Expected exactly one `timestampPath`!")

    protected val versionOfPath = store.iter(s = identifier, p = LDES.versionOfPath).singleOrNull()?.o
        as? Quad.NamedTerm ?: streamFormatError("Expected exactly one `versionOfPath`!")

    abstract val members: List<Member>

    /**
     * All various (distinct) [timestampPath] values of the individual members, sorted according to the used comparator
     *  implementation.
     */
    abstract val timestamps: List<Quad.Literal>

    init {
        if (!store.iter(s = identifier, p = RDF.type, o = LDES.EventStream).hasNext()) {
            streamFormatError("Stream $identifier does not have the event stream type set!")
        }
    }

    /* public api */

    abstract fun read(until: Quad.Literal): Store

    /**
     * Read a specific version of a member (identified using [base]) at a given point in time (according
     *  to [timestampValue]). The additional [inclusive] flag dictates whether versions with a [timestampValue]
     *  identical to the one provided are allowed.
     */
    abstract fun read(base: Quad.NamedTerm, timestampValue: Quad.Literal, inclusive: Boolean = true): StreamElement?

    /* build up methods */

    protected fun materializeVersionedMembers(store: Store): MutableList<Member> =
        store
            .iter(s = identifier, p = LDES.member)
            .mapTo(mutableListOf()) {
                val identifier = it.o as? Quad.NamedTerm
                    ?: streamFormatError("Member $identifier is not an IRI")
                materialize(store, identifier)
            }

    protected fun materialize(
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

    protected fun streamFormatError(message: String): Nothing =
        throw InvalidStreamFormatException(identifier, message)

}
