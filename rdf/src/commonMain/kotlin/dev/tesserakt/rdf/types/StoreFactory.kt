package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.*

fun MutableStore(): MutableStore = MutableStoreImpl()

fun MutableStore(capacity: Int): MutableStore = MutableStoreImpl(capacity)

fun MutableStore(data: Collection<Quad>): MutableStore = MutableStoreImpl(data)

fun ObservableStore(): ObservableStore = ObservableStoreImpl()

fun ObservableStore(data: Collection<Quad>): ObservableStore = ObservableStoreImpl(data)

fun IndexedStore(data: Collection<Quad>): IndexedStore = IndexedStoreImpl(data)

fun Store(): Store = EmptyStoreImpl

fun Store(quads: Collection<Quad>): Store {
    if (quads.isEmpty()) {
        return EmptyStoreImpl
    }
    return StoreImpl(quads)
}

fun Store(quads: Iterable<Quad>, sizeHint: Int = 10): Store {
    if (quads is Collection<Quad>) {
        return Store(quads)
    }
    return StoreImpl(quads, sizeHint)
}

fun storeOf(): Store = EmptyStoreImpl

fun emptyStore(): Store = EmptyStoreImpl

fun storeOf(vararg quad: Quad): Store = if (quad.isEmpty()) EmptyStoreImpl else StoreImpl(quad.toSet())

fun mutableStoreOf(vararg quad: Quad): MutableStore = MutableStore(data = quad.toSet())

fun indexedStoreOf(vararg quad: Quad): IndexedStore = IndexedStoreImpl(data = quad.toSet())
