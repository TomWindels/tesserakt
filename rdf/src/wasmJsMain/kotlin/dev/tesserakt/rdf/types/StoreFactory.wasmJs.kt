package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.StoreImpl

actual fun Store(quads: Collection<Quad>): Store {
    return StoreImpl(quads)
}

actual fun Store(quads: Iterable<Quad>, sizeHint: Int): Store {
    return StoreImpl(quads, sizeHint)
}
