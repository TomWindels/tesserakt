package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import kotlin.jvm.JvmInline

@JvmInline
value class TermIdentifier(val id: Int) {

    companion object {
        fun QueryContext.get(term: TermIdentifier): Quad.Term = resolveTerm(id = term.id)
    }

}
