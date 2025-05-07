package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.types.*


interface StreamTransform<StreamUnit> {

    /**
     * Encodes the [element]s data, inserting it into the [target]. The [hint] identifier may be used for this new
     *  element, but is not guaranteed: the actual element identifier is always returned instead.
     *
     * @return the used identifier, which may be equal to the provided [hint] identifier
     */
    fun encode(target: MutableStore, element: StreamUnit, hint: Quad.NamedTerm): Quad.NamedTerm

    /**
     * Decodes data associated with [identifier] from the [source]
     */
    fun decode(source: Store, identifier: Quad.NamedTerm): StreamUnit

    /**
     * Decodes all data associated with the various [identifiers] from the [source], mapping it into a [Store]
     */
    fun decode(source: Store, identifiers: Set<Quad.NamedTerm>): Store

    object GraphBased : StreamTransform<Store> {

        override fun encode(target: MutableStore, element: Store, hint: Quad.NamedTerm): Quad.NamedTerm {
            target.addAll(element.map { it.copy(g = hint) })
            return hint
        }

        override fun decode(source: Store, identifier: Quad.NamedTerm): Store {
            return source
                .mapNotNullTo(mutableSetOf()) { if (it.g == identifier) it.copy(g = Quad.DefaultGraph) else null }
                .toStore()
        }

        override fun decode(source: Store, identifiers: Set<Quad.NamedTerm>): Store {
            return source.mapNotNull { if (it.g in identifiers) it.copy(g = Quad.DefaultGraph) else null }.toStore()
        }

    }

}
