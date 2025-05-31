package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.util.Cardinality

class RehashableMappingArray(
    private var indexes: BindingIdentifierSet,
    private var active: MappingArray
) : MappingArray {

    override val cardinality: Cardinality
        get() = active.cardinality

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

    override fun addAll(mappings: Iterable<Mapping>) {
        active.addAll(mappings)
    }

    override fun remove(mapping: Mapping) {
        active.remove(mapping)
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        active.removeAll(mappings)
    }

    fun rehash(context: QueryContext, bindings: Iterable<String>) {
        rehash(bindings = BindingIdentifierSet(context, bindings))
    }

    fun rehash(bindings: BindingIdentifierSet) {
        if (indexes == bindings) {
            return
        }
        val new = MappingArray(bindings)
        active.iter().forEach { new.add(it) }
        active = new
        indexes = bindings
    }

    override fun toString(): String {
        return "$active (rehashable)"
    }

}
