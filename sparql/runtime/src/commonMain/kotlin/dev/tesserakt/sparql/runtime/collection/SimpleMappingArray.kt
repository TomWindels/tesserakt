package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.CollectedStream
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.util.removeLastElement
import kotlin.jvm.JvmInline

@JvmInline
value class SimpleMappingArray(
    private val mappings: ArrayList<Mapping> = ArrayList()
): MappingArray {

    constructor(mappings: Collection<Mapping>): this(ArrayList(mappings))

    override val cardinality: Cardinality
        get() = Cardinality(mappings.size)

    override val indexes: BindingIdentifierSet
        get() = BindingIdentifierSet.EMPTY

    val size get() = mappings.size

    override fun iter(mappings: List<Mapping>): List<CollectedStream<Mapping>> {
        // the parameter is unused as we're not indexed
        val stream = iter()
        return List(mappings.size) { stream }
    }

    override fun iter(mapping: Mapping): CollectedStream<Mapping> {
        // the parameter is unused as we're not indexed
        return iter()
    }

    override fun iter(): CollectedStream<Mapping> = CollectedStream(mappings)

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
                throw NoSuchElementException("$mapping cannot be removed from SimpleMappingArray - not found!")
            }
            this.mappings.size - 1 -> {
                this.mappings.removeLastElement()
            }
            else -> {
                // putting the last element there instead
                this.mappings[i] = this.mappings.removeLastElement()
            }
        }
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach(::remove)
    }

    override fun toString() = "SimpleMappingArray (cardinality ${cardinality})"

}
