package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping

class BindingsImpl(private val context: QueryContext, private val mapping: Mapping): Bindings {

    private val iterable = mapping.asIterable(context)

    override fun iterator(): Iterator<Pair<String, Quad.Term>> = iterable.iterator()

    fun retain(names: Set<String>) = BindingsImpl(context, mapping = mapping.retain(BindingIdentifierSet(context, names)))

    override fun hashCode(): Int {
        return mapping.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BindingsImpl) {
            return false
        }
        val a = mapping.asIterable().iterator()
        val b = other.mapping.asIterable().iterator()
        while (a.hasNext() && b.hasNext()) {
            if (a.next() != b.next()) {
                return false
            }
        }
        return !a.hasNext() && !b.hasNext()
    }

    override fun toString() = iterable.joinToString(prefix = "{", postfix = "}") { "${it.first} = ${it.second}" }

}
