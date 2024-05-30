package dev.tesserakt.sparql.runtime.incremental.patterns.rules

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.incremental.types.Pattern

internal abstract class QueryRule<DT: Any> {

    /**
     * Creates a new state object for that specific rule instance
     */
    abstract fun newState(): DT

    companion object {

        /* helpers for using the pattern element types */

        val Pattern.Subject.bindingName: String?
            get() = (this as? Pattern.Binding)?.name

        val Pattern.Object.bindingName: String?
            get() = (this as? Pattern.Binding)?.name

        val Pattern.Predicate.bindingName: String?
            get() = (this as? Pattern.Binding)?.name

        fun Pattern.Subject.matches(term: Quad.Term): Boolean =
            (this !is Pattern.Exact || this.term == term)

        fun Pattern.Object.matches(term: Quad.Term): Boolean =
            (this !is Pattern.Exact || this.term == term)

        fun Pattern.Predicate.matches(term: Quad.Term): Boolean =
            this is Pattern.RegularBinding || (this as Pattern.FixedPredicate).matches(term)

        fun Pattern.FixedPredicate.matches(term: Quad.Term): Boolean =
            when (this) {
                is Pattern.Alts -> allowed.any { it.matches(term) }
                is Pattern.Exact -> this.term == term
                is Pattern.Inverse -> this.predicate != term
            }

    }

}
