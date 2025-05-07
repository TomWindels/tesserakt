package dev.tesserakt.rdf.types

class ReadOnlyStore(override val quads: Set<Quad>): Store() {
    constructor(data: Iterable<Quad>): this(data.toSet())
}
