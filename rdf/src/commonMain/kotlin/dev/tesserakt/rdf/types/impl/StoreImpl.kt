package dev.tesserakt.rdf.types.impl

import dev.tesserakt.concurrent.ConcurrentSet
import dev.tesserakt.concurrent.globalTaskRunner
import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.Quad

// not required here: we optimized hash code as we're readonly, but the equals check stays in place
@Suppress("EqualsOrHashCode")
internal class StoreImpl: AbstractStore {

    private val quads: Set<EncodedQuad>
    override val context: EncodingContext

    // considering the contents don't change, we can cache the collection's hash code
    private val hashCode by lazy { super.hashCode() }

    constructor(data: Collection<Quad>) {
        // if the collection is big enough, we do it concurrently, for faster context encoding
        val ctx: MutableEncodingContext
        val set: Set<EncodedQuad>
        val runner = globalTaskRunner
        runner.buffered(data.iterator()).use { iter ->
            if (iter.supportsConcurrentAccess() && data.size > 10_000) {
                set = ConcurrentSet(data.size)
                ctx = MutableEncodingContext {
                    initialCapacity = data.size
                    concurrent = true
                }
                // the quad encoding & storing process is at worst 2x slower than
                //  a very fast source iterator (e.g. reading from disk)
                //  so we limit our reading parallelization to 2
                runner.parallelize(2) {
                    while (true) {
                        val q = iter.getNext() ?: break
                        val encoded = EncodedQuad(ctx, q)
                        set.add(encoded)
                    }
                }.await()
            } else {
                // regular evaluation
                set = HashSet()
                ctx = MutableEncodingContext {
                    initialCapacity = data.size
                }
                while (true) {
                    val q = iter.getNext() ?: break
                    val encoded = EncodedQuad(ctx, q)
                    set.add(encoded)
                }
            }
        }
        this.quads = set
        this.context = ctx
    }

    constructor(quads: Iterable<Quad>, sizeHint: Int) {
        val ctx: MutableEncodingContext
        val set: Set<EncodedQuad>
        val runner = globalTaskRunner
        runner.buffered(quads.iterator()).use { iter ->
            if (iter.supportsConcurrentAccess()) {
                set = ConcurrentSet(sizeHint)
                ctx = MutableEncodingContext {
                    initialCapacity = sizeHint
                    concurrent = true
                }
                // the quad encoding & storing process is at worst 2x slower than
                //  a very fast source iterator (e.g. reading from disk)
                //  so we limit our reading parallelization to 2
                runner.parallelize(2) {
                    while (true) {
                        val q = iter.getNext() ?: break
                        val encoded = EncodedQuad(ctx, q)
                        set.add(encoded)
                    }
                }.await()
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
        this.quads = set
        this.context = ctx
    }

    constructor(context: EncodingContext, quads: Set<EncodedQuad>) {
        this.context = context
        this.quads = quads
    }

    override val size: Int
        get() = quads.size

    override fun encodedIterator(): Iterator<EncodedQuad> = quads.iterator()

    override fun isEmpty(): Boolean {
        return quads.isEmpty()
    }

    override fun contains(element: Quad): Boolean {
        // two possible scenarios where we don't contain a given quad:
        // * either our immutable context doesn't have it, in which case we don't contain a quad element this quad uses,
        //  and thus cannot possibly contain the quad, or
        // * we have all individual quad elements present in our context, but not in this
        //  specific 'configuration' (s, p, o and g)
        val encoded = EncodedQuad(context, element)
            // case 1: the quad has an element we don't even have an encoded representation for; so we don't have the
            //  quad itself either
            ?: return false
        // case 2: we have to check the encoded representation in our collection
        return quads.contains(encoded)
    }

    override fun hashCode(): Int {
        return hashCode
    }

}
