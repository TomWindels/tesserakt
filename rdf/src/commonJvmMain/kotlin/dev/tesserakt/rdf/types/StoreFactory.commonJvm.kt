package dev.tesserakt.rdf.types

import dev.tesserakt.concurrent.globalTaskRunner
import dev.tesserakt.rdf.types.impl.EmptyStoreImpl
import dev.tesserakt.rdf.types.impl.MutableEncodingContextImpl
import dev.tesserakt.rdf.types.impl.StoreImpl
import java.util.concurrent.ConcurrentHashMap

actual fun Store(quads: Collection<Quad>): Store {
    if (quads.isEmpty()) {
        return EmptyStoreImpl
    }
    // if the collection is big enough, we do it concurrently, for faster context encoding
    val ctx: MutableEncodingContext
    val set: Set<EncodedQuad>
    val runner = globalTaskRunner
    runner.buffered(quads.iterator()).use { iter ->
        if (iter.supportsConcurrentAccess() && quads.size > 10_000) {
            set = ConcurrentHashMap.newKeySet(quads.size)
            ctx = MutableEncodingContext {
                initialCapacity = quads.size
                concurrent = true
            }
            // FIXME '3'
            List(3) {
                runner.dispatch {
                    while (true) {
                        val q = iter.getNext() ?: break
                        val encoded = EncodedQuad(ctx, q)
                        set.add(encoded)
                    }
                }
            }.forEach { it.await() }
        } else {
            // regular evaluation
            set = HashSet()
            ctx = MutableEncodingContext {
                initialCapacity = quads.size
            }
            while (true) {
                val q = iter.getNext() ?: break
                val encoded = EncodedQuad(ctx, q)
                set.add(encoded)
            }
        }
    }
    return StoreImpl(
        context = ctx,
        quads = set,
    )
}

actual fun Store(quads: Iterable<Quad>, sizeHint: Int): Store {
    if (quads is Collection<Quad>) {
        return Store(quads)
    }
    val ctx: MutableEncodingContext
    val set: Set<EncodedQuad>
    val runner = globalTaskRunner
    runner.buffered(quads.iterator()).use { iter ->
        if (iter.supportsConcurrentAccess()) {
            set = ConcurrentHashMap.newKeySet(sizeHint)
            ctx = MutableEncodingContext {
                initialCapacity = sizeHint
                concurrent = true
            }
            // FIXME '3'
            List(3) {
                runner.dispatch {
                    while (true) {
                        val q = iter.getNext() ?: break
                        val encoded = EncodedQuad(ctx, q)
                        set.add(encoded)
                    }
                }
            }.forEach { it.await() }
        } else {
            // regular evaluation
            set = HashSet(sizeHint)
            ctx = MutableEncodingContextImpl(sizeHint)
            while (true) {
                val q = iter.getNext() ?: break
                val encoded = EncodedQuad(ctx, q)
                set.add(encoded)
            }
        }
    }
    return StoreImpl(
        context = ctx,
        quads = set,
    )
}
