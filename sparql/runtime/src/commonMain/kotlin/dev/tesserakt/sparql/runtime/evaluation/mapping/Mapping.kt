package dev.tesserakt.sparql.runtime.evaluation.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.util.bitIterator
import dev.tesserakt.util.cloneTo
import kotlin.jvm.JvmInline

/**
 * The internal representation of a solution to a SELECT query.
 *
 * IMPORTANT: this does not provide consistent [hashCode] and [equals] results!
 *  If this is required (e.g. lookup in a map), wrap it using [HashableMapping]!
 *
 * See [matches] to check mappings for equality!
 */
@JvmInline
value class Mapping internal constructor(
    // a binding mask, followed by all term values associated with these various bindings
    internal val data: IntArray,
) {

    private constructor(bindings: Int, data: Collection<Int>): this(
        data = IntArray(data.size + 1).apply {
            this[0] = bindings
            var i = 1
            for (v in data) {
                this[i++] = v
            }
        }
    )

    constructor(context: QueryContext, source: Map<String, Quad.Element>): this(
        bindings = source.asIterable().fold(initial = 0) { acc, entry -> acc or (1 shl context.resolveBinding(entry.key)) },
        data = source.asIterable().sortedBy { context.resolveBinding(it.key) }.map { context.resolveTerm(it.value) },
    )

    constructor(context: QueryContext, source: Iterable<Pair<String, Quad.Element>>): this(
        bindings = source.fold(initial = 0) { acc, entry -> acc or (1 shl context.resolveBinding(entry.first)) },
        data = source.sortedBy { context.resolveBinding(it.first) }.map { context.resolveTerm(it.second) },
    )

    constructor(source: Iterable<Pair<BindingIdentifier, TermIdentifier>>): this(
        bindings = source.fold(initial = 0) { acc, entry -> acc or (1 shl entry.first.id) },
        data = source.sortedBy { it.first.id }.map { it.second.id },
    )

    val bindings: BindingIdentifierSet
        get() = BindingIdentifierSet.fromMask(data[0])

    /**
     * The number of terms bound in this instance
     */
    val count: Int
        get() = data.size - 1

    fun get(binding: BindingIdentifier): TermIdentifier? {
        // getting the binding index associated with `binding`
        val index = bindingIndex(target = binding.id)
        if (index == -1) {
            return null
        }
        return TermIdentifier(data[index])
    }

    fun retain(bindings: BindingIdentifierSet): Mapping {
        val remaining = bindings.asIntIterable().fold(0) { acc, i -> acc or (1 shl i) }
        val common = this.bindings.mask and remaining
        val iter = common.bitIterator()
        val data = IntArray(common.countOneBits() + 1)
        data[0] = common
        var i = 1
        while (iter.hasNext()) {
            data[i++] = this.data[bindingIndex(iter.nextInt())]
        }
        return Mapping(data)
    }

    fun compatibleWith(other: Mapping): Boolean {
        return count(other) != -1
    }

    fun join(other: Mapping): Mapping? {
        if (this.bindings.mask == 0) {
            return other
        }
        if (other.bindings.mask == 0) {
            return this
        }
        val c = count(other)
        if (c == -1) {
            // incompatible mappings
            return null
        }
        // not having this inlined helps enormously with performance
        return joinUnchecked(this, other)
    }

    fun asIterable(context: QueryContext) = object: Iterable<Pair<String, Quad.Element>> {
        override fun iterator() = object: Iterator<Pair<String, Quad.Element>> {
            private val iterator = this@Mapping.asIterable().iterator()

            override fun hasNext(): Boolean {
                return iterator.hasNext()
            }

            override fun next(): Pair<String, Quad.Element> {
                val (bId, tId) = iterator.next()
                return context.resolveBinding(bId.id) to context.resolveTerm(tId.id)
            }
        }
    }

    fun asIterable() = object: Iterable<Pair<BindingIdentifier, TermIdentifier>> {
        override fun iterator() = object: Iterator<Pair<BindingIdentifier, TermIdentifier>> {
            private val iter = bindings.iterator()
            private var i = 1

            override fun hasNext(): Boolean {
                return iter.hasNext()
            }

            override fun next(): Pair<BindingIdentifier, TermIdentifier> {
                val binding = iter.nextInt()
                val term = i++
                return BindingIdentifier(binding) to TermIdentifier(data[term])
            }
        }
    }

    fun values(): TermIdentifierSet {
        return TermIdentifierSet(data.sliceArray(1 ..< data.size))
    }

    /**
     * Value-aware [Any.equals] check. Should be used instead of the `==` operator
     */
    fun matches(other: Mapping): Boolean {
        return data.contentEquals(other.data)
    }

    private fun count(other: Mapping): Int {
        val common = bindings.mask and other.bindings.mask
        // ensuring all those that are in common, are in fact identical; if there aren't any, no checks are required
        val iter = common.bitIterator()
        while (iter.hasNext()) {
            val bindingId = iter.nextInt()
            if (get(bindingId) != other.get(bindingId)) {
                return -1
            }
        }
        // as all bindings are either not in common, or identical, the total "sum" of these binding pairs is the result
        return (bindings.mask or other.bindings.mask).countOneBits()
    }

    internal fun get(binding: Int): Int {
        return data[bindingIndex(binding)]
    }

    /**
     * Calculates the index in [data] the term value associated with the binding [target], or `-1` if this instance has
     *  no value associated with that binding.
     */
    private fun bindingIndex(target: Int): Int {
        // ensuring it exists
        if ((1 shl target) and bindings.mask == 0) {
            return -1
        }
        // method: changing the `bindings` field to only contain all bits lower than our target,
        //  and counting how many bits of those are set, as every bit set represents a slot (and thus index)
        //  that should be skipped
        // we also need to increment it by one, as the first slot is taken up by the `bindings` value
        return (((1 shl target) - 1) and bindings.mask).countOneBits() + 1
    }

    override fun toString(): String {
        return "BitsetMapping { bindings: 0x${bindings.mask.toHexString(format = HexFormat { upperCase = true })}, terms: [${(1 ..< data.size).joinToString { data[it].toString() }}] }"
    }

    companion object {

        val EMPTY = Mapping(intArrayOf(0))

    }

}

// having this split up here helps with performance, as the happy and unhappy path
//  can be optimized separately in JIT
private fun joinUnchecked(
    leftMapping: Mapping,
    rightMapping: Mapping,
): Mapping {
    val output = IntArray((leftMapping.bindings.mask or rightMapping.bindings.mask).countOneBits() + 1)
    output[0] = leftMapping.bindings.mask or rightMapping.bindings.mask
    var i = 1
    val a = leftMapping.bindings.mask.bitIterator()
    val b = rightMapping.bindings.mask.bitIterator()
    var left = a.nextInt()
    var right = b.nextInt()
    while (true) {
        when {
            left < right -> {
                output[i++] = leftMapping.get(left)
                left = if (a.hasNext()) {
                    a.nextInt()
                } else {
                    // all other elements from the right side can be added right away
                    val remaining = b.remaining() + 1
                    rightMapping.data.cloneTo(
                        target = output,
                        thisOffset = rightMapping.data.size - remaining,
                        targetOffset = i,
                        length = remaining
                    )
                    break
                }
            }
            right < left -> {
                output[i++] = rightMapping.get(right)
                right = if (b.hasNext()) {
                    b.nextInt()
                } else {
                    // all other elements from the left side can be added right away
                    val remaining = a.remaining() + 1
                    leftMapping.data.cloneTo(
                        target = output,
                        thisOffset = leftMapping.data.size - remaining,
                        targetOffset = i,
                        length = remaining
                    )
                    break
                }
            }
            else /* right == left */ -> {
                // no equality check required; `count` took care of that
                output[i++] = leftMapping.get(left)
                left = if (a.hasNext()) {
                    a.nextInt()
                } else {
                    // all other elements from the right side can be added right away
                    // this first step, `result[i++] = other.get(right)`, is not required, as here, left == right
                    val remaining = b.remaining()
                    rightMapping.data.cloneTo(
                        target = output,
                        thisOffset = rightMapping.data.size - remaining,
                        targetOffset = i,
                        length = remaining
                    )
                    break
                }
                right = if (b.hasNext()) {
                    b.nextInt()
                } else {
                    // all other elements from the left side can be added right away
                    val remaining = a.remaining() + 1
                    leftMapping.data.cloneTo(
                        target = output,
                        thisOffset = leftMapping.data.size - remaining,
                        targetOffset = i,
                        length = remaining
                    )
                    break
                }
            }
        }
    }
    return Mapping(output)
}
