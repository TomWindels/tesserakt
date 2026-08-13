package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.util.bitIterator
import kotlin.jvm.JvmInline

@JvmInline
value class BindingIdentifierSet private constructor(val mask: Int) {

    constructor(vararg ids: Int) :
            this(mask = ids.fold(0) { acc, i -> acc or (1 shl i) })

    constructor(ids: Iterable<Int>) :
            this(mask = ids.fold(0) { acc, i -> acc or (1 shl i) })

    constructor(context: QueryContext, names: Iterable<String>) :
            this(mask = names.fold(0) { acc, name -> acc or (1 shl context.resolveBinding(name)) })

    val size: Int
        get() = mask.countOneBits()

    fun isEmpty(): Boolean {
        // identical to size == 0, without having to do a pop count
        return mask == 0
    }

    fun isNotEmpty(): Boolean {
        // identical to size != 0, without having to do a pop count
        return mask != 0
    }

    fun asIntIterable() = object: Iterable<Int> {
        override fun iterator(): IntIterator {
            return mask.bitIterator()
        }
    }

    fun iterator(): IntIterator {
        return mask.bitIterator()
    }

    fun intersectSize(other: BindingIdentifierSet): Int {
        return (mask and other.mask).countOneBits()
    }

    fun unionSize(other: BindingIdentifierSet): Int {
        return (mask or other.mask).countOneBits()
    }

    fun intersect(other: BindingIdentifierSet): BindingIdentifierSet {
        return BindingIdentifierSet(mask and other.mask)
    }

    operator fun get(index: Int): BindingIdentifier {
        // ensuring it exists
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Invalid index $index for mask with $size element(s)")
        }
        val iter = mask.bitIterator()
        repeat(index) {
            iter.nextInt()
        }
        return BindingIdentifier(iter.nextInt())
    }

    operator fun contains(element: Int): Boolean {
        return (1 shl element) and mask != 0
    }

    operator fun contains(elements: BindingIdentifierSet): Boolean {
        return mask and elements.mask == elements.mask
    }

    operator fun plus(other: BindingIdentifierSet): BindingIdentifierSet {
        return BindingIdentifierSet(mask or other.mask)
    }

    operator fun minus(other: BindingIdentifierSet): BindingIdentifierSet {
        return BindingIdentifierSet(mask and (other.mask.inv()))
    }

    override fun toString() = asIntIterable().joinToString(prefix = "BindingIdentifierSet {", postfix = "}")

    companion object {

        val EMPTY = BindingIdentifierSet(0)

        // implemented as a companion object factory method to prevent overload ambiguity with the vararg variant,
        //  which is more common
        fun fromMask(mask: Int): BindingIdentifierSet {
            return BindingIdentifierSet(mask)
        }
    }

}
