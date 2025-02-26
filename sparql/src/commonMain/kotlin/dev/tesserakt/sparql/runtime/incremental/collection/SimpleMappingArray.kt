package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.stream.CollectedStream
import kotlin.jvm.JvmInline

@JvmInline
internal value class SimpleMappingArray(
    override val mappings: ArrayList<Mapping> = ArrayList()
): MappingArray {

    constructor(mappings: Collection<Mapping>): this(ArrayList(mappings))

    override val cardinality: Int
        get() = mappings.size

    override fun iter(mappings: List<Mapping>): List<CollectedStream<Mapping>> {
        return List(mappings.size) { CollectedStream(this.mappings) }
    }

    override fun iter(mapping: Mapping): CollectedStream<Mapping> {
        return CollectedStream(mappings)
    }

    override fun add(mapping: Mapping) {
        this.mappings.add(mapping)
    }

    override fun addAll(mappings: Iterable<Mapping>) {
        this.mappings.addAll(mappings)
    }

    override fun remove(mapping: Mapping) {
        val i = this.mappings.indexOfLast { it == mapping }
        when (i) {
            -1 -> {
                throw IllegalStateException("$mapping cannot be removed from NestedJoinArray - not found!")
            }
            this.mappings.size - 1 -> {
                this.mappings.removeLast()
            }
            else -> {
                // putting the last element there instead
                this.mappings[i] = this.mappings.removeLast()
            }
        }
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach(::remove)
    }

    override fun toString() = "SimpleJoinArray (cardinality ${mappings.size})"

}
