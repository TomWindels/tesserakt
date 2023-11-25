package core.sparql.compiler

import core.rdf.Triple
import kotlin.jvm.JvmInline

data class Pattern(
    val s: Subject,
    val p: Predicate,
    val o: Object
) {
    // collection of all possible subject pattern element types
    sealed interface Subject
    // collection of all possible predicate pattern element types
    sealed interface Predicate
    // collection of all possible object pattern element types
    sealed interface Object
    // collection of types possible at any spot (s, p & o)
    sealed interface Element: Subject, Predicate, Object
    /**
     * "any" value: otherwise unused binding, or constraint open enough to be any
     *  value predicate (e.g. `(<predicate>|!<predicate>)`)
     */
    data object WildCard: Element
    /** reused binding (either within the corresponding pattern, or inside the (sub)query's exports **/
    @JvmInline value class Binding(val name: String): Element
    /** "exact" value, e.g. `<predicate>` **/
    @JvmInline value class Exact(val value: Triple.Term): Element

    /** inverse of "exact" value, e.g. `(!<predicate>)` **/
    @JvmInline value class Not(val value: Triple.Term): Predicate
    /** set of allowed types only, e.g. `(<predicate1>|<predicate2>)` **/
    // would this ever have to be expanded to support subject & objects?
    @JvmInline value class Constrained(val allowed: List<Predicate>): Predicate
    /** regular predicate, but repeated an arbitrary amount of times (`<predicate>*`) **/
    @JvmInline value class Repeating(val value: Predicate): Predicate
    /** multiple predicates chained together, e.g. <predicate1>/<predicate2> **/
    @JvmInline value class Chain(val list: List<Predicate>): Predicate
}
