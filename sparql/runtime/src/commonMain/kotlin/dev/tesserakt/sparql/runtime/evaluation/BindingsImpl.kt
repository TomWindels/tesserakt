package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings

class BindingsImpl(private val context: QueryContext, private val mapping: Mapping): Bindings {

    private val iterable = mapping.asIterable(context)

    override fun iterator(): Iterator<Pair<String, Quad.Term>> = iterable.iterator()

    fun retain(names: Set<String>) = BindingsImpl(context, mapping = mapping.retain(context, names))

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
