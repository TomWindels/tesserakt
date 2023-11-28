package core.sparql.compiler.types

import core.rdf.types.Triple
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

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

    /** reused binding (either within the corresponding pattern, or inside the (sub)query's exports **/
    @JvmInline
    value class Binding(val name: String): Element
    /** "exact" value, e.g. `<predicate>` **/
    @JvmInline
    value class Exact(val value: Triple.Term): Element

    /** inverse of "exact" value, e.g. `(!<predicate>)` **/
    @JvmInline
    value class Not(val predicate: Predicate): Predicate
    /** set of allowed types only, e.g. `(<predicate1>|<predicate2>)` **/
    // would this ever have to be expanded to support subject & objects?
    @JvmInline
    value class Constrained(val allowed: List<Predicate>): Predicate {
        constructor(vararg predicates: Predicate): this(predicates.flatMap { if (it is Constrained) it.allowed else listOf(it) })
    }
    /** multiple predicates chained together, e.g. <predicate1>/<predicate2> **/
    @JvmInline
    value class Chain(val list: List<Predicate>): Predicate {
        constructor(vararg predicates: Predicate): this(predicates.flatMap { if (it is Chain) it.list else listOf(it) })
    }
    /** regular predicate, but repeated an arbitrary amount of times (`<predicate>*`) **/
    @JvmInline
    value class Repeating(val value: Predicate): Predicate


    companion object {

        @JvmStatic
        internal fun Token.Term.asBinding(): Binding? {
            return if (value[0] == '?') {
                Binding(value.substring(1))
            } else {
                null
            }
        }

    }

}
