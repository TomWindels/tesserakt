package dev.tesserakt

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier

class LowBindingCountMapping private constructor(
    // self-managed bitmask
    private val bindings: Int,
    // all term values associated with the various bindings above
    private val terms: IntArray,
) {

    constructor(context: QueryContext, source: Map<String, Quad.Term>): this(
        bindings = source.asIterable().fold(initial = 0) { acc, entry -> acc or (1 shl context.resolveBinding(entry.key)) },
        terms = source.asIterable().sortedBy { context.resolveBinding(it.key) }.map { context.resolveTerm(it.value) }.toIntArray(),
    )

    constructor(context: QueryContext, source: Iterable<Pair<String, Quad.Term>>): this(
        bindings = source.asIterable().fold(initial = 0) { acc, entry -> acc or context.resolveBinding(entry.first) },
        terms = source.asIterable().sortedBy { context.resolveBinding(it.first) }.map { context.resolveTerm(it.second) }.toIntArray(),
    )

    private val hashCode = bindings + terms.contentHashCode()

    fun get(binding: BindingIdentifier): TermIdentifier? {
        // getting the binding index associated with `binding`
        val index = bindingIndex(target = binding.id)
        if (index == -1) {
            return null
        }
        return TermIdentifier(terms[index])
    }

    fun join(other: LowBindingCountMapping): LowBindingCountMapping? {
        if (this.bindings == 0) {
            return other
        }
        if (other.bindings == 0) {
            return this
        }
        val c = count(other)
        if (c == -1) {
            // incompatible mappings
            return null
        }
        val terms = IntArray(c)
        var i = 0
        val a = this.bindings.bitIterator()
        val b = other.bindings.bitIterator()
        var left = a.next()
        var right = b.next()
        while (true) {
            when {
                left < right -> {
                    terms[i++] = this.get(left)
                    left = if (a.hasNext()) {
                        a.next()
                    } else {
                        // all other elements to the right can get added right away
                        terms[i++] = other.get(right)
                        b.forEach {
                            terms[i++] = other.get(it)
                        }
                        break
                    }
                }
                right < left -> {
                    terms[i++] = other.get(right)
                    right = if (b.hasNext()) {
                        b.next()
                    } else {
                        // all other elements to the right can get added right away
                        terms[i++] = this.get(left)
                        a.forEach {
                            terms[i++] = this.get(it)
                        }
                        break
                    }
                }
                else /* right == left */ -> {
                    // no equality check required; `count` took care of that
                    terms[i++] = this.get(left)
                    left = if (a.hasNext()) {
                        a.next()
                    } else {
                        // all other elements to the right can get added right away
                        // this first step, `terms[i++] = other.get(right)`, is not required, as here, left == right
                        b.forEach {
                            terms[i++] = other.get(it)
                        }
                        break
                    }
                    right = if (b.hasNext()) {
                        b.next()
                    } else {
                        // all other elements to the right can get added right away
                        terms[i++] = this.get(left)
                        // FIXME use `remaining()` instead to avoid unnecessary `get()` calls
                        a.forEach {
                            terms[i++] = this.get(it)
                        }
                        break
                    }
                }
            }
        }
        return LowBindingCountMapping(
            bindings = bindings or other.bindings,
            terms = terms,
        )
    }

    private fun count(other: LowBindingCountMapping): Int {
        val common = bindings and other.bindings
        // ensuring all those that are in common, are in fact identical; if there aren't any, no checks are required
        common.bitIterator().forEach { bindingId ->
            if (get(bindingId) != other.get(bindingId)) {
                return -1
            }
        }
        // as all bindings are either not in common, or identical, the total "sum" of these binding pairs is the result
        return (bindings or other.bindings).countOneBits()
    }

    private fun get(binding: Int): Int {
        return terms[bindingIndex(binding)]
    }

    private fun bindingIndex(target: Int): Int {
        // ensuring it exists
        if ((1 shl target) and bindings == 0) {
            return -1
        }
        // method: changing the `bindings` field to only contain all bits lower than our target,
        //  and counting how many bits of those are set, as every bit set represents a slot (and thus index)
        //  that should be skipped
        return (((1 shl target) - 1) and bindings).countOneBits()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "Mapping { bindings: 0x${bindings.toHexString(format = HexFormat { upperCase = true })}, terms: [${terms.joinToString()}] }"
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LowBindingCountMapping) {
            return false
        }
        if (hashCode != other.hashCode) {
            return false
        }
        return bindings == other.bindings && terms.contentEquals(other.terms)
    }

}

private fun Int.bitIterator() = BitIterator(this)

private class BitIterator(private var bits: Int): Iterator<Int> {
    override fun hasNext(): Boolean {
        return bits != 0
    }

    override fun next(): Int {
        val bit = bits.takeLowestOneBit()
        val index = bit.countTrailingZeroBits()
        bits = bits xor bit
        return index
    }

    fun remaining(): Int {
        return bits.countOneBits()
    }
}
