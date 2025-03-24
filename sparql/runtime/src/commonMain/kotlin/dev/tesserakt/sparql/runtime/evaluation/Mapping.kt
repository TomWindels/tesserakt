package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.isNullOr
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

@JvmInline
value class Mapping private constructor(private val data: IntIntPair?) {

    constructor(source: Map<String, Quad.Term>): this(data = convert(source))

    constructor(source: Iterable<Pair<String, Quad.Term>>): this(data = convert(source))

    init {
        require(data.isNullOr { it.count > 0 })
    }

    fun keys() = object: Iterable<String> {
        override fun iterator(): Iterator<String> = object: Iterator<String> {

            private var i = 0

            override fun hasNext(): Boolean {
                val data = this@Mapping.data
                return data != null && i < data.count
            }

            override fun next(): String {
                return BINDING_RLUT[this@Mapping.data!!.key(i++)]!!
            }
        }
    }

    fun asIterable() = object: Iterable<Pair<String, Quad.Term>> {

        override fun iterator() = object: Iterator<Pair<String, Quad.Term>> {

            private var i = 0

            override fun hasNext(): Boolean {
                val data = this@Mapping.data
                return data != null && i < data.count
            }

            override fun next(): Pair<String, Quad.Term> {
                data as IntIntPair
                return (BINDING_RLUT[this@Mapping.data.key(i)]!! to TERM_RLUT[this@Mapping.data.value(i)]!!).also { ++i }
            }
        }

    }

    fun join(other: Mapping): Mapping? {
        return when (val count = count(data, other.data)) {
            -1 -> null
            0 -> Mapping(null)
            else -> {
                Mapping(combine(data, other.data, count))
            }
        }
    }

    operator fun plus(other: Mapping): Mapping = join(other)!!

    fun compatibleWith(other: Mapping): Boolean {
        return count(this.data, other.data) != -1
    }

    fun retain(names: Set<String>): Mapping {
        if (data == null) {
            return this
        }
        val resolved = names
            .mapNotNullTo(mutableSetOf()) { binding -> resolveBinding(binding).takeIf { data.search(it) != -1 } }
            .ifEmpty { return EmptyMapping }
        val result = IntArray(resolved.size * 2)
        var i = 0
        resolved.sorted().forEach { id ->
            result[i] = id
            result[i + 1] = this[id]!!
            i += 2
        }
        return Mapping(data = result.into())
    }

    fun toMap(): Map<String, Quad.Term> {
        return if (data == null) emptyMap() else buildMap(data.count) {
            repeat(data.count) {
                put(BINDING_RLUT[data.key(it)]!!, TERM_RLUT[data.value(it)]!!)
            }
        }
    }

    fun isEmpty() = data == null /* zero-sized data is not allowed! */

    operator fun get(binding: String): Quad.Term? {
        return TERM_RLUT[get(resolveBinding(binding))]
    }

    private operator fun get(id: Int): Int? = when {
        data == null -> null
        else -> data.search(id).takeIf { it != -1 }?.let { data.value(it) }
    }

    companion object {

        // FIXME this one can be "fixed" based on the query
        private val BINDING_LUT = mutableMapOf<String, Int>()
        // FIXME this one can be an array
        private val BINDING_RLUT = mutableMapOf<Int, String>()
        private val TERM_LUT = mutableMapOf<Quad.Term, Int>()
        private val TERM_RLUT = mutableMapOf<Int, Quad.Term>()

        private fun convert(input: Map<String, Quad.Term>): IntIntPair? {
            return if (input.isEmpty()) null else input.map { resolveBinding(it.key) to resolveTerm(it.value) }.sortedBy { it.first }.flatten().into()
        }

        private fun convert(input: Iterable<Pair<String, Quad.Term>>): IntIntPair? {
            return if (!input.iterator().hasNext()) null else input.map { resolveBinding(it.first) to resolveTerm(it.second) }.sortedBy { it.first }.flatten().into()
        }

        private fun resolveBinding(name: String): Int {
            return BINDING_LUT.getOrPut(name) { BINDING_RLUT[BINDING_LUT.size] = name; BINDING_LUT.size }
        }

        private fun resolveTerm(term: Quad.Term): Int {
            return TERM_LUT.getOrPut(term) { TERM_RLUT[TERM_RLUT.size] = term; TERM_LUT.size }
        }

        private fun List<Pair<Int, Int>>.flatten(): IntArray {
            val result = IntArray(size * 2)
            forEachIndexed { i, (a, b) ->
                result[2 * i] = a
                result[2 * i + 1] = b
            }
            return result
        }

        /**
         * Counts the total number of bindings that would be part of this mapping, or -1 if incompatible
         */
        private fun count(left: IntIntPair?, right: IntIntPair?): Int {
            when {
                left == null && right == null -> {
                    return 0
                }
                left == null && right != null -> {
                    return right.count
                }
                right == null && left != null -> {
                    return left.count
                }
                else -> {
                    left!!
                    right!!
                    var pos1 = 0
                    var pos2 = 0
                    var count = 0
                    while (true) {
                        when {
                            left.key(pos1) < right.key(pos2) -> {
                                count += 1
                                ++pos1
                                if (pos1 == left.count) {
                                    return count + right.count - pos2
                                }
                            }
                            left.key(pos1) > right.key(pos2) -> {
                                count += 1
                                ++pos2
                                if (pos2 == right.count) {
                                    return count + left.count - pos1
                                }
                            }
                            else /* == */ -> {
                                if (left.value(pos1) != right.value(pos2)) {
                                    return -1
                                }
                                count += 1
                                ++pos1
                                ++pos2
                                if (pos1 == left.count) {
                                    return count + right.count - pos2
                                }
                                if (pos2 == right.count) {
                                    return count + left.count - pos1
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun combine(left: IntIntPair?, right: IntIntPair?, size: Int): IntIntPair {
            require(left != null || right != null)
            if (left == null) {
                return right!!
            }
            if (right == null) {
                return left
            }
            val result = IntArray(size * 2)
            var pos1 = 0
            var pos2 = 0
            var i = 0
            while (true) {
                when {
                    left.key(pos1) < right.key(pos2) -> {
                        result[i] = left.key(pos1)
                        result[i + 1] = left.value(pos1)
                        ++pos1
                        if (pos1 == left.count) {
                            while (pos2 < right.count) {
                                i += 2
                                result[i] = right.key(pos2)
                                result[i + 1] = right.value(pos2)
                                ++pos2
                            }
                            return result.into()
                        }
                    }
                    left.key(pos1) > right.key(pos2) -> {
                        result[i] = right.key(pos2)
                        result[i + 1] = right.value(pos2)
                        ++pos2
                        if (pos2 == right.count) {
                            while (pos1 < left.count) {
                                i += 2
                                result[i] = left.key(pos1)
                                result[i + 1] = left.value(pos1)
                                ++pos1
                            }
                            return result.into()
                        }
                    }
                    else /* == */ -> {
                        result[i] = left.key(pos1)
                        result[i + 1] = left.value(pos1)
                        ++pos1
                        ++pos2
                        if (pos1 == left.count) {
                            while (pos2 < right.count) {
                                i += 2
                                result[i] = right.key(pos2)
                                result[i + 1] = right.value(pos2)
                                ++pos2
                            }
                            return result.into()
                        }
                        if (pos2 == right.count) {
                            while (pos1 < left.count) {
                                i += 2
                                result[i] = left.key(pos1)
                                result[i + 1] = left.value(pos1)
                                ++pos1
                            }
                            return result.into()
                        }
                    }
                }
                i += 2
            }
        }

        private fun IntArray.into() = IntIntPair(value = this)

    }

    private class IntIntPair(private val value: IntArray) {

        val count = value.size / 2
        private val hashCode = value.contentHashCode()

        fun key(index: Int): Int {
            return value[2 * index]
        }

        fun value(index: Int): Int {
            return value[2 * index + 1]
        }

        override fun toString() = (0..<count).joinToString { "${key(it)}: ${value(it)}" }

        override fun equals(other: Any?): Boolean {
            if (other !is IntIntPair) {
                return false
            }
            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return hashCode
        }

        /**
         * Returns the index associated with [keyValue], or -1 if not found.
         * The following holds true:
         * ```kt
         * val result = data.search(2)
         * (result == -1 || data.key(result) == 2) == true
         * ```
         * This makes it possible to retrieve the value associated with that key
         * ```kt
         * data.value(result) // value associated with `id=2`
         * ```
         */
        fun search(keyValue: Int): Int {
            var min = 0
            var max = count - 1
            while (min <= max) {
                val mid = min + (max - min) / 2
                val ele = key(mid)
                when {
                    keyValue == ele -> return mid
                    keyValue < ele -> max = mid - 1
                    ele < keyValue -> min = mid + 1
                }
            }
            return -1
        }

    }

}

val EmptyMapping = Mapping(emptyList())

fun mappingOf(vararg pairs: Pair<String, Quad.Term>): Mapping =
    Mapping(pairs.asIterable())

@JvmName("mappingOfNullable")
fun mappingOf(vararg pairs: Pair<String?, Quad.Term>): Mapping =
    @Suppress("UNCHECKED_CAST")
    Mapping(pairs.filter { it.first != null } as List<Pair<String, Quad.Term>>)

fun emptyMapping(): Mapping = EmptyMapping
