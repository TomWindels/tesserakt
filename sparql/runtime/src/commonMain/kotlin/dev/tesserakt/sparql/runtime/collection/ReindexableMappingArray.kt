package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.util.Cardinality

class ReindexableMappingArray(
    bindings: BindingIdentifierSet,
    private var hint: MappingArrayHint = MappingArrayHint(),
) : MappingArray {

    private var active: MappingArray = MappingArray(bindings, hint)

    override val cardinality: Cardinality
        get() = active.cardinality

    override val indexes: BindingIdentifierSet
        get() = active.indexes

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        return active.iter(mapping)
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        return active.iter(mappings)
    }

    override fun iter(): OptimisedStream<Mapping> {
        return active.iter()
    }

    override fun add(mapping: Mapping) {
        active.add(mapping)
    }

    override fun addAll(mappings: Iterable<Mapping>): Int {
        return active.addAll(mappings)
    }

    override fun remove(mapping: Mapping) {
        active.remove(mapping)
    }

    override fun removeAll(mappings: Iterable<Mapping>): Int {
        return active.removeAll(mappings)
    }

    fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint? = null) {
        val hint = hint ?: this.hint
        this.hint = hint
        val new = MappingArray(bindings, hint)
        // if the new array has the same properties given the hint and index configuration, we can skip
        //  the replacement step
        if (active::class.isInstance(new) && new.indexes == active.indexes) {
            return
        }
        active.iter().forEach { new.add(it) }
        active = new
    }

    override fun toString(): String {
        return "$active (rehashable)"
    }

}
