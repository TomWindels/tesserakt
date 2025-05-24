package dev.tesserakt.sparql.runtime.evaluation.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.util.bitIterator

class BitsetMapping private constructor(
    // self-managed bitmask
    private val bindings: Int,
    // all term values associated with the various bindings above
    private val terms: IntArray,
) : Mapping {

    constructor(context: QueryContext, source: Map<String, Quad.Term>): this(
        bindings = source.asIterable().fold(initial = 0) { acc, entry -> acc or (1 shl context.resolveBinding(entry.key)) },
        terms = source.asIterable().sortedBy { context.resolveBinding(it.key) }.map { context.resolveTerm(it.value) }.toIntArray(),
    )

    constructor(context: QueryContext, source: Iterable<Pair<String, Quad.Term>>): this(
        bindings = source.asIterable().fold(initial = 0) { acc, entry -> acc or (1 shl context.resolveBinding(entry.first)) },
        terms = source.asIterable().sortedBy { context.resolveBinding(it.first) }.map { context.resolveTerm(it.second) }.toIntArray(),
    )

    private val hashCode = bindings + terms.contentHashCode()

    override fun get(binding: BindingIdentifier): TermIdentifier? {
        // getting the binding index associated with `binding`
        val index = bindingIndex(target = binding.id)
        if (index == -1) {
            return null
        }
        return TermIdentifier(terms[index])
    }

    override fun retain(bindings: BindingIdentifierSet): Mapping {
        val remaining = bindings.asIntIterable().fold(0) { acc, i -> acc or (1 shl i) }
        val common = this.bindings and remaining
        val iter = common.bitIterator()
        val terms = IntArray(common.countOneBits()) { terms[bindingIndex(iter.next())] }
        return BitsetMapping(
            bindings = common,
            terms = terms,
        )
    }

    override fun compatibleWith(other: Mapping): Boolean {
        require(other is BitsetMapping)
        return count(other) != -1
    }

    override fun isEmpty(): Boolean {
        return bindings == 0
    }

    override fun join(other: Mapping): BitsetMapping? {
        require(other is BitsetMapping)
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
        return BitsetMapping(
            bindings = bindings or other.bindings,
            terms = terms,
        )
    }

    override fun keys(context: QueryContext) = object: Iterable<String> {
        override fun iterator() = object: Iterator<String> {
            private val iter = bindings.bitIterator()

            override fun hasNext(): Boolean {
                return iter.hasNext()
            }

            override fun next(): String {
                return context.resolveBinding(iter.next())
            }
        }
    }

    override fun asIterable(context: QueryContext) = object: Iterable<Pair<String, Quad.Term>> {
        override fun iterator() = object: Iterator<Pair<String, Quad.Term>> {
            private val iterator = this@BitsetMapping.asIterable().iterator()

            override fun hasNext(): Boolean {
                return iterator.hasNext()
            }

            override fun next(): Pair<String, Quad.Term> {
                val (bId, tId) = iterator.next()
                return context.resolveBinding(bId.id) to context.resolveTerm(tId.id)
            }
        }
    }

    override fun asIterable() = object: Iterable<Pair<BindingIdentifier, TermIdentifier>> {
        override fun iterator() = object: Iterator<Pair<BindingIdentifier, TermIdentifier>> {
            private val iter = bindings.bitIterator()
            private var i = 0

            override fun hasNext(): Boolean {
                return iter.hasNext()
            }

            override fun next(): Pair<BindingIdentifier, TermIdentifier> {
                val binding = iter.next()
                val term = i++
                return BindingIdentifier(binding) to TermIdentifier(terms[term])
            }
        }
    }

    override fun toMap(context: QueryContext): Map<String, Quad.Term> {
        return asIterable(context).toMap()
    }

    override fun get(context: QueryContext, binding: String): Quad.Term? {
        val index = bindingIndex(context.resolveBinding(binding))
        return if (index == -1) {
            null
        } else {
            context.resolveTerm(terms[index])
        }
    }

    private fun count(other: BitsetMapping): Int {
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
        return "BitsetMapping { bindings: 0x${bindings.toHexString(format = HexFormat { upperCase = true })}, terms: [${terms.joinToString()}] }"
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BitsetMapping) {
            return false
        }
        if (hashCode != other.hashCode) {
            return false
        }
        return bindings == other.bindings && terms.contentEquals(other.terms)
    }

    companion object {

        val EMPTY = BitsetMapping(0, intArrayOf())

    }

}
