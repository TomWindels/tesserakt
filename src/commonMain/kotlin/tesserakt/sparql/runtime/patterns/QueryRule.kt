package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import kotlin.jvm.JvmInline

internal sealed class QueryRule<DT: Any> {

    /**
     * Creates a new state object for that specific rule instance
     */
    abstract fun newState(): DT

    sealed interface Element {

        sealed interface Predicate

        @JvmInline
        value class Exact(val term: Triple.Term) : Element, Predicate

        @JvmInline
        value class Binding(val name: String) : Element, Predicate

        @JvmInline
        value class Inverse(val term: Triple.Term) : Predicate

        @JvmInline
        value class InverseEither(val elements: List<Exact>) : Predicate

        @JvmInline
        value class Either(val elements: List<Exact>) : Predicate

        companion object {

            fun Element.matches(term: Triple.Term): Boolean =
                (this !is Exact || this.term == term)

            fun Predicate.matches(term: Triple.Term): Boolean = when (this) {
                is Binding -> true
                is Exact -> this.term == term
                is Inverse -> this.term != term
                is Either -> elements.any { it.term == term }
                is InverseEither -> elements.none { it.term == term }
            }

            fun MutableMap<String, Triple.Term>.insert(element: Element, value: Triple.Term) {
                if (element is Binding) {
                    this[element.name] = value
                }
            }

            fun MutableMap<String, Triple.Term>.insert(element: Predicate, value: Triple.Term) {
                when (element) {
                    is Binding -> { this[element.name] = value }
                    is Either -> { /* nothing to insert */ }
                    is Exact -> { /* nothing to insert */ }
                    is Inverse -> { /* nothing to insert */ }
                    is InverseEither -> { /* nothing to insert */ }
                }
            }

        }

    }

}
