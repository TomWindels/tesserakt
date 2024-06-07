package dev.tesserakt.sparql.runtime.core.pattern

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern

internal sealed class TriplePattern {

    internal data class NonRepeating(
        val s: Pattern.Subject,
        val p: Pattern.NonRepeatingPredicate,
        val o: Pattern.Object
    ) : TriplePattern()

    internal data class Repeating(
        val s: Pattern.Subject,
        val p: Pattern.FixedPredicate,
        val o: Pattern.Object,
        val type: Type
    ) : TriplePattern() {
        enum class Type {
            ZERO_OR_MORE, ONE_OR_MORE
        }
    }

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
