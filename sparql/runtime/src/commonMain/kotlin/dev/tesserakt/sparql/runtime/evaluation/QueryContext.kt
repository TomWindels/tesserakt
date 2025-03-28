package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad

interface QueryContext {

    fun resolveBinding(value: String): Int

    fun resolveTerm(value: Quad.Term): Int

    fun resolveBinding(id: Int): String

    fun resolveTerm(id: Int): Quad.Term

}
