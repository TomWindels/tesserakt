package dev.tesserakt.sparql.runtime.evaluation.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext

interface Mapping {

    fun join(other: Mapping): Mapping?

    fun keys(context: QueryContext): Iterable<String>

    fun asIterable(context: QueryContext): Iterable<Pair<String, Quad.Element>>

    fun asIterable(): Iterable<Pair<BindingIdentifier, TermIdentifier>>

    fun toMap(context: QueryContext): Map<String, Quad.Element>

    fun get(context: QueryContext, binding: String): Quad.Element?

    fun get(binding: BindingIdentifier): TermIdentifier?

    fun retain(bindings: BindingIdentifierSet): Mapping

    fun compatibleWith(other: Mapping): Boolean

    fun isEmpty(): Boolean

}
