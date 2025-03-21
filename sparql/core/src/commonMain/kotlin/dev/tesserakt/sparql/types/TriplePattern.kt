package dev.tesserakt.sparql.types

import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

data class TriplePattern(
    val s: Subject,
    val p: Predicate,
    val o: Object,
) : QueryAtom {

    sealed interface Element : QueryAtom

    sealed interface Subject : Element

    sealed interface Predicate : Element

    sealed interface Object : Element

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
    value class NamedBinding(override val name: String) : Binding {
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
    value class Negated(val terms: SimpleAlts) : UnboundPredicate, StatelessPredicate {
        override fun toString() = "!${terms}"
    }

    @JvmInline
    value class Alts(val allowed: List<UnboundPredicate>) : UnboundPredicate {
        constructor(a: UnboundPredicate, b: UnboundPredicate): this(
            allowed = when {
                a is Alts && b is Alts -> a.allowed + b.allowed

                a is Alts -> a.allowed + b

                b is Alts -> b.allowed + a // order doesn't matter

                else -> listOf(a, b)
            }
        )

        override fun toString() = allowed.joinToString(
            separator = " | ",
            prefix = "(",
            postfix = ")",
            transform = { "($it)" }
        )
    }

    @JvmInline
    value class SimpleAlts(val allowed: List<StatelessPredicate>) : UnboundPredicate, StatelessPredicate {
        constructor(a: StatelessPredicate, b: StatelessPredicate): this(
            allowed = when {
                a is SimpleAlts && b is SimpleAlts -> a.allowed + b.allowed

                a is SimpleAlts -> a.allowed + b

                b is SimpleAlts -> b.allowed + a // order doesn't matter

                else -> listOf(a, b)
            }
        )

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
        constructor(a: Predicate, b: Predicate): this(
            chain = when {
                a is Sequence && b is Sequence -> a.chain + b.chain

                a is Sequence -> a.chain + b

                b is Sequence -> buildList(b.chain.size + 1) {
                    add(a)
                    addAll(b.chain)
                }

                else -> listOf(a, b)
            }
        )

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
        constructor(a: UnboundPredicate, b: UnboundPredicate): this(
            chain = when {
                a is UnboundSequence && b is UnboundSequence -> a.chain + b.chain

                a is UnboundSequence -> a.chain + b

                b is UnboundSequence -> buildList(b.chain.size + 1) {
                    add(a)
                    addAll(b.chain)
                }

                else -> listOf(a, b)
            }
        )

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
