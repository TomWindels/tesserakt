package tesserakt.sparql.runtime.patterns.rules

import tesserakt.rdf.types.Triple
import kotlin.jvm.JvmInline

internal abstract class QueryRule<DT: Any> {

    /**
     * Creates a new state object for that specific rule instance
     */
    abstract fun newState(): DT

    sealed interface Element

    sealed interface Predicate

    // all predicates not representing a binding
    sealed interface FixedPredicate

    @JvmInline
    value class Binding(val name: String) : Element, Predicate

    @JvmInline
    value class Exact(val term: Triple.Term) : Element, FixedPredicate, Predicate

    @JvmInline
    value class Inverse(val term: FixedPredicate) : FixedPredicate, Predicate

    @JvmInline
    value class Either(val elements: List<FixedPredicate>) : FixedPredicate, Predicate

    companion object {

        /* helpers for using these element types */

        val Element.bindingName: String?
            get() = (this as? Binding)?.name

        val Predicate.bindingName: String?
            get() = (this as? Binding)?.name

        fun Element.matches(term: Triple.Term): Boolean =
            (this !is Exact || this.term == term)

        fun Predicate.matches(term: Triple.Term): Boolean =
            this is Binding || (this as FixedPredicate).matches(term)

        fun FixedPredicate.matches(term: Triple.Term): Boolean = when (this) {
            is Exact -> this.term == term
            is Inverse -> !this.term.matches(term)
            is Either -> elements.any { it.matches(term) }
        }

    }

}
