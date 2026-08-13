package dev.tesserakt.rdf.types.factory

import dev.tesserakt.rdf.types.IndexedStore
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.impl.EmptyStoreImpl
import dev.tesserakt.rdf.types.impl.IndexedStoreImpl
import dev.tesserakt.rdf.types.impl.StoreImpl

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.MutableStore()"),
)
fun MutableStore() = MutableStore()

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.MutableStore(data)"),
)
fun MutableStore(data: Collection<Quad>) = MutableStore(data)

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.ObservableStore()"),
)
fun ObservableStore() = ObservableStore()

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.ObservableStore(data)"),
)
fun ObservableStore(data: Collection<Quad>) = ObservableStore(data)

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.IndexedStore(data)"),
)
fun IndexedStore(data: Collection<Quad>) = IndexedStore(data)

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.Store()"),
)
fun Store() = Store()

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.Store(quads)"),
)
fun Store(quads: Collection<Quad>) = Store(quads)

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.storeOf()"),
)
fun storeOf(): Store = EmptyStoreImpl

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.emptyStore()"),
)
fun emptyStore() = Store()

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.storeOf(quad)"),
)
fun storeOf(vararg quad: Quad): Store = if (quad.isEmpty()) EmptyStoreImpl else StoreImpl(quad.toSet())

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.mutableStoreOf(quad)"),
)
fun mutableStoreOf(vararg quad: Quad) = MutableStore(data = quad.toSet())

@Deprecated(
    message = "Use the version in the `types` package instead",
    replaceWith = ReplaceWith("dev.tesserakt.rdf.types.indexedStoreOf(quad)"),
)
fun indexedStoreOf(vararg quad: Quad): IndexedStore = IndexedStoreImpl(data = quad.toSet())
