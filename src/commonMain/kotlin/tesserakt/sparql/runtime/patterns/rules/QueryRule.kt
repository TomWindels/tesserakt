package tesserakt.sparql.runtime.patterns.rules

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.PatternASTr

internal abstract class QueryRule<DT: Any> {

    /**
     * Creates a new state object for that specific rule instance
     */
    abstract fun newState(): DT

    companion object {

        /* helpers for using the pattern element types */

        val PatternASTr.Subject.bindingName: String?
            get() = (this as? PatternASTr.Binding)?.name

        val PatternASTr.Object.bindingName: String?
            get() = (this as? PatternASTr.Binding)?.name

        val PatternASTr.Predicate.bindingName: String?
            get() = (this as? PatternASTr.Binding)?.name

        fun PatternASTr.Subject.matches(term: Triple.Term): Boolean =
            (this !is PatternASTr.Exact || this.term == term)

        fun PatternASTr.Object.matches(term: Triple.Term): Boolean =
            (this !is PatternASTr.Exact || this.term == term)

        fun PatternASTr.Predicate.matches(term: Triple.Term): Boolean =
            this is PatternASTr.RegularBinding || (this as PatternASTr.FixedPredicate).matches(term)

        fun PatternASTr.FixedPredicate.matches(term: Triple.Term): Boolean =
            when (this) {
                is PatternASTr.Alts -> allowed.any { it.matches(term) }
                is PatternASTr.Exact -> this.term == term
                is PatternASTr.Inverse -> this.predicate != term
            }

    }

}
