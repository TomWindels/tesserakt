package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping

interface QueryContext {

    fun resolveBinding(value: String): Int

    fun resolveTerm(value: Quad.Element): Int

    fun resolveBinding(id: Int): String

    fun resolveTerm(id: Int): Quad.Element

    fun create(terms: Iterable<Pair<String, Quad.Element>>): Mapping

    fun emptyMapping(): Mapping

}
