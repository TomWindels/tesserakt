package dev.tesserakt.rdf.types

fun Iterable<Quad>.toStore(): Store {
    return Store().apply { addAll(this@toStore) }
}

fun Iterator<Quad>.consume(target: Store = Store()): Store {
    forEach { target.add(it) }
    return target
}
