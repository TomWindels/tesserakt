package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping

interface QueryContext {

    fun resolveBinding(value: String): Int

    fun resolveTerm(value: Quad.Term): Int

    fun resolveBinding(id: Int): String

    fun resolveTerm(id: Int): Quad.Term

    fun create(terms: Iterable<Pair<String, Quad.Term>>): Mapping

    fun emptyMapping(): Mapping

}
