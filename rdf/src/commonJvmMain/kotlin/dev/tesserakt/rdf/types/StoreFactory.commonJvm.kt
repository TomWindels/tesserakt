package dev.tesserakt.rdf.types

import dev.tesserakt.concurrent.globalTaskRunner
import dev.tesserakt.rdf.types.impl.EmptyStoreImpl
import dev.tesserakt.rdf.types.impl.StoreImpl
import dev.tesserakt.rdf.types.impl.TieredEncodingContextImpl
import dev.tesserakt.rdf.types.impl.concurrent
import java.util.concurrent.ConcurrentHashMap

actual fun Store(quads: Collection<Quad>): Store {
    if (quads.isEmpty()) {
        return EmptyStoreImpl
    }
    // if the collection is big enough, we do it concurrently, for faster context encoding
    val ctx: MutableEncodingContext
    val set: Set<EncodedQuad>
    val runner = globalTaskRunner
    val iter = runner.buffered(quads.iterator())
    if (iter.supportsConcurrentAccess() && quads.size > 10_000) {
        set = ConcurrentHashMap.newKeySet(quads.size)
        ctx = TieredEncodingContextImpl.concurrent()
        repeat(5) {
            runner.dispatch {
                while (true) {
                    val q = iter.getNext() ?: break
                    val encoded = EncodedQuad(ctx, q)
                    set.add(encoded)
                }
            }
        }
    } else {
        // regular evaluation
        set = mutableSetOf()
        ctx = TieredEncodingContextImpl()
        while (true) {
            val q = iter.getNext() ?: break
            val encoded = EncodedQuad(ctx, q)
            set.add(encoded)
        }
    }
    return StoreImpl(
        context = ctx,
        quads = set,
    )
}

actual fun Store(quads: Iterable<Quad>): Store {
    if (quads is Collection<Quad>) {
        return Store(quads)
    }
    val ctx: MutableEncodingContext
    val set: Set<EncodedQuad>
    val runner = globalTaskRunner
    val iter = runner.buffered(quads.iterator())
    if (iter.supportsConcurrentAccess()) {
        set = ConcurrentHashMap.newKeySet()
        ctx = TieredEncodingContextImpl.concurrent()
        repeat(5) {
            runner.dispatch {
                while (true) {
                    val q = iter.getNext() ?: break
                    val encoded = EncodedQuad(ctx, q)
                    set.add(encoded)
                }
            }
        }
    } else {
        // regular evaluation
        set = mutableSetOf()
        ctx = TieredEncodingContextImpl()
        while (true) {
            val q = iter.getNext() ?: break
            val encoded = EncodedQuad(ctx, q)
            set.add(encoded)
        }
    }
    return StoreImpl(
        context = ctx,
        quads = set,
    )
}
