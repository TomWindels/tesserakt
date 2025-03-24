package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings

class BindingsImpl(private val mapping: Mapping): Bindings {

    private val iterable = mapping.asIterable()

    override fun iterator(): Iterator<Pair<String, Quad.Term>> = iterable.iterator()

    fun retain(names: Set<String>) = BindingsImpl(mapping = mapping.retain(names))

    override fun hashCode(): Int {
        return mapping.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BindingsImpl) {
            return false
        }
        return mapping == other.mapping
    }

    override fun toString() = iterable.joinToString(prefix = "{", postfix = "}") { "${it.first} = ${it.second}" }

}
