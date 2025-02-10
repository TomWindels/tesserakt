package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.stream.ldes.ontology.LDES
import kotlinx.datetime.Instant
import kotlin.math.sign

class VersionedLinkedDataEventStream<StreamElement>(
    val identifier: Quad.NamedTerm,
    val store: Store,
    private val transform: StreamTransform<StreamElement>
) {

    private data class VersionedMember(
        /**
         * The corresponding version identifier, i.e. `#post1v0`
         */
        val identifier: Quad.NamedTerm,
        /**
         * The base version identifier, i.e. `#post1`, if any
         */
        val base: Quad.NamedTerm?,
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

    init {
        if (store.none { it.s == identifier && it.p == RDF.type && it.o == LDES.EventStream }) {
            streamFormatError("Stream $identifier has no ")
        }
    }

    /* public api */

    fun get(until: Quad.Literal): Store {
        // FIXME subset of relevant based on overlapping version base identifier
        val relevant = members.filter { it.timestampValue <= until }
        return transform.decode(store, relevant.mapTo(mutableSetOf()) { it.identifier })
    }

    fun add(
        base: Quad.NamedTerm,
        version: Quad.Literal,
        data: StreamElement,
    ) {
        val hint = Quad.NamedTerm("${base.value}#v${members.count { it.base == base }}")
        val element = transform.encode(target = store, element = data, hint = hint)
        store.add(Quad(identifier, LDES.member, element))
        store.add(Quad(element, timestampPath, version))
        store.add(Quad(element, versionOfPath, base))
        members.add(
            VersionedMember(
                identifier = element,
                base = base,
                timestampValue = version,
            )
        )
    }

    companion object {

        fun <StreamUnit> initialise(
            identifier: Quad.NamedTerm,
            timestampPath: Quad.NamedTerm,
            versionOfPath: Quad.NamedTerm,
            transform: StreamTransform<StreamUnit>
        ): VersionedLinkedDataEventStream<StreamUnit> = VersionedLinkedDataEventStream(
            identifier = identifier,
            transform = transform,
            store = Store()
                .apply {
                    // minimum set of triples required for a valid versioned LDES with the provided arguments
                    add(Quad(identifier, RDF.type, LDES.EventStream))
                    add(Quad(identifier, LDES.timestampPath, timestampPath))
                    add(Quad(identifier, LDES.versionOfPath, versionOfPath))
                }
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
            base = store.singleOrNull { it.s == identifier && it.p == versionOfPath }?.o as? Quad.NamedTerm,
            timestampValue = store.singleOrNull { it.s == identifier && it.p == timestampPath }?.o as? Quad.Literal
                ?: streamFormatError("Member $identifier has an incorrect amount of triples with predicate $timestampPath associated, or is not a literal term"),
        )
    }

    private fun streamFormatError(message: String): Nothing =
        throw InvalidStreamFormatException(identifier, message)

}

private operator fun Quad.Literal.compareTo(rhs: Quad.Literal): Int {
    if (type != rhs.type) {
        throw IllegalArgumentException("Cannot compare different literal datatypes when comparing $this with $rhs")
    }
    return when {
        type == XSD.date ->
            Instant.parse(value).minus(Instant.parse(rhs.value)).inWholeNanoseconds.sign

        else ->
            TODO("Not yet implemented for $type")
    }
}

private class InvalidStreamFormatException(
    identifier: Quad.NamedTerm,
    message: String
): RuntimeException("Stream $identifier is not correctly formatted! $message")
