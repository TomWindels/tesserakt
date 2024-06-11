package dev.tesserakt.sparql.runtime.core.pattern

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern

internal data class TriplePattern(
    val s: Pattern.Subject,
    val p: Pattern.Predicate,
    val o: Pattern.Object
) {

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

        fun Pattern.Exact.matches(term: Quad.Term): Boolean =
            term == this.term

        fun Pattern.Predicate.matches(term: Quad.Term): Boolean = when (this) {
            /* all of these contain a binding, so automatically, it matches any term */
            is Pattern.RegularBinding -> true
            is Pattern.Alts -> true
            is Pattern.GeneratedBinding -> true
            /* all of these match only a subset of terms, so checking manually */
            is Pattern.Exact -> term == this.term
            is Pattern.UnboundAlts -> allowed.any { it.matches(term) }
            is Pattern.UnboundInverse -> !predicate.matches(term)
            /* these cannot be directly matched to terms, so bailing */
            is Pattern.Chain,
            is Pattern.UnboundChain -> throw IllegalArgumentException("Chains cannot be directly matched with terms!")
            is Pattern.OneOrMore,
            is Pattern.ZeroOrMore -> throw IllegalArgumentException("Repeating patterns cannot be directly matched with terms!")
        }

    }

}
