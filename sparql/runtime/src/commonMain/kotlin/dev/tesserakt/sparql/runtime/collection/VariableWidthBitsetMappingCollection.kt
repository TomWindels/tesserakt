package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping


class VariableWidthBitsetMappingCollection: Iterable<BitsetMapping> {

    @Deprecated(
        message = "It's not recommended to mutate the buffer directly",
        replaceWith = ReplaceWith("insert {}"),
    )
    val buf: GrowingIntArray = GrowingIntArray()
    var size = 0
        @Deprecated(
            message = "It's not recommended to set the size directly",
            replaceWith = ReplaceWith("insert {}")
        )
        set

    fun add(mapping: BitsetMapping) {
        // these are intentional uses of 'deprecated' APIs
        @Suppress("DEPRECATION")
        buf.add(mapping.bindings)
        @Suppress("DEPRECATION")
        mapping.terms.iterator().forEach { buf.add(it) }
        @Suppress("DEPRECATION")
        ++size
    }

    // if necessary, a 'remove' can be added, replacing all mapping int's with a sentinel value, which
    //  can then later be overwritten upon subsequent `add` with identical length
    // this is currently not implemented as none of the iterations/join methods are currently expecting such
    //  sentinel values to be present

    // intentional uses of 'deprecated' APIs
    @Suppress("DEPRECATION")
    fun intIterator() = buf.iterator()

    inline fun insert(block: (GrowingIntArray) -> Unit) {
        // these are intentional uses of 'deprecated' APIs
        @Suppress("DEPRECATION")
        block(buf)
        @Suppress("DEPRECATION")
        ++size
    }

    override fun iterator(): Iterator<BitsetMapping> = object: Iterator<BitsetMapping> {
        private val iter = intIterator()

        override fun hasNext(): Boolean {
            return iter.hasNext()
        }

        override fun next(): BitsetMapping {
            val bindings = iter.next()
            val count = bindings.countOneBits()
            val arr = IntArray(count) { iter.next() }
            return BitsetMapping(
                bindings = bindings,
                terms = arr
            )
        }
    }

    companion object {
        operator fun invoke(data: Iterable<BitsetMapping>): VariableWidthBitsetMappingCollection {
            val result = VariableWidthBitsetMappingCollection()
            data.forEach { result.add(it) }
            return result
        }
    }

}
