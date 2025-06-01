package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import kotlin.jvm.JvmInline

sealed interface TermIdentifier {
    val id: Int

    @JvmInline
    value class Materialized(override val id: Int): TermIdentifier {

        constructor(context: QueryContext, element: Quad.Element): this(id = context.resolveTerm(element))

    }

    class Pending(
        val context: QueryContext,
        val element: Quad.Element,
    ): TermIdentifier {

        override val id: Int by lazy {
            context.resolveTerm(element)
        }

    }

    companion object {

        operator fun invoke(id: Int) = Materialized(id)

        // FIXME: make this a pending if it doesnt exist yet, materialized if it does
        operator fun invoke(context: QueryContext, element: Quad.Element) = Pending(context, element)

        fun QueryContext.get(term: TermIdentifier): Quad.Element = when (term) {
            is Materialized -> resolveTerm(id = term.id)
            is Pending -> term.element
        }

    }

}
