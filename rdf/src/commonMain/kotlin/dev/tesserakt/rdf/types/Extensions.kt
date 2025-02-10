package dev.tesserakt.rdf.types

fun Iterable<Quad>.toStore(): Store {
    return Store().apply { addAll(this@toStore) }
}
