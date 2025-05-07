package dev.tesserakt.rdf.types


fun Iterable<Quad>.toStore(): Store {
    return ReadOnlyStore(toSet())
}

fun Iterator<Quad>.toStore(): Store {
    val result = mutableSetOf<Quad>()
    forEach { result.add(it) }
    return ReadOnlyStore(result)
}
