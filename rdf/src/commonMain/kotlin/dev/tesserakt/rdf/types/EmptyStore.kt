package dev.tesserakt.rdf.types

data object EmptyStore: Store() {
    override val quads: Set<Quad>
        get() = emptySet()
}
