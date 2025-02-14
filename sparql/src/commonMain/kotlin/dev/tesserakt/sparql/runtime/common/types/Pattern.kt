package dev.tesserakt.sparql.runtime.common.types

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.node.CommonNode
import kotlin.jvm.JvmInline

data class Pattern(
    val s: Subject,
    val p: Predicate,
    val o: Object,
) : CommonNode {

    sealed interface Subject : CommonNode

    sealed interface Predicate : CommonNode

    sealed interface Object : CommonNode

    /**
     * Subset of predicates: those that can function (peek functionality) w/o maintaining state
     */
    sealed interface StatelessPredicate: Predicate

    /**
     * Subset of predicates: those that are guaranteed to not contain any bindings
     */
    sealed interface UnboundPredicate : Predicate

    sealed interface RepeatingPredicate: UnboundPredicate {
        val element: UnboundPredicate
    }

    sealed interface Binding : Subject, Predicate, Object {
        val name: String
    }

    /**
     * Bindings coming directly from an entered query
     */
    @JvmInline
    value class RegularBinding(override val name: String) : Binding {
        override fun toString() = "?$name"
    }

    /**
     * Bindings generated by the runtime for use in query rules only. Differ from regular bindings in the way they are
     *  presented back to the user in string form
     */
    @JvmInline
    value class GeneratedBinding(val id: Int) : Binding {
        override val name: String get() = " b${id}"
        override fun toString() = "?$name"
    }

    @JvmInline
    value class Exact(val term: Quad.Term) : Subject, UnboundPredicate, StatelessPredicate, Object {
        override fun toString() = term.toString()
    }

    // FIXME: inverse can either be the inverse of a term (this case) AS WELL AS the inverse of a path (^iri)
    @JvmInline
    value class Negated(val term: Quad.Term) : UnboundPredicate, StatelessPredicate {
        override fun toString() = "!($term)"
    }

    @JvmInline
    value class Alts(val allowed: List<UnboundPredicate>) : UnboundPredicate {
        override fun toString() = allowed.joinToString(
            separator = " | ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    @JvmInline
    value class SimpleAlts(val allowed: List<StatelessPredicate>) : UnboundPredicate, StatelessPredicate {
        override fun toString() = allowed.joinToString(
            separator = " | ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    /*
    cannot always be destructured using generated bindings, as they sometimes appear in repeating or inverse structures;
    in the repeating cases, bindings are not allowed, so this version cannot be used
    */
    @JvmInline
    value class Sequence(val chain: List<Predicate>) : Predicate {
        override fun toString() = chain.joinToString(
            separator = " / ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    /*
    cannot always be destructured using generated bindings, as they sometimes appear in repeating or inverse structures
    */
    @JvmInline
    value class UnboundSequence(val chain: List<UnboundPredicate>) : UnboundPredicate {
        override fun toString() = chain.joinToString(" / ")
    }

    @JvmInline
    value class ZeroOrMore(override val element: UnboundPredicate): RepeatingPredicate, UnboundPredicate {
        override fun toString() = "($element)*"
    }

    @JvmInline
    value class OneOrMore(override val element: UnboundPredicate): RepeatingPredicate, UnboundPredicate {
        override fun toString() = "($element)+"
    }

    override fun toString() = "$s $p $o"

}
