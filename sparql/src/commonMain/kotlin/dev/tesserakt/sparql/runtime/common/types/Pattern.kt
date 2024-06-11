package dev.tesserakt.sparql.runtime.common.types

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.node.CommonNode
import kotlin.jvm.JvmInline

data class Pattern(
    val s: Subject,
    val p: Predicate,
    val o: Object,
): CommonNode {

    sealed interface Subject: CommonNode

    sealed interface Predicate: CommonNode

    sealed interface Object: CommonNode

    sealed interface UnboundPredicate: Predicate

    sealed interface RepeatingPredicate: UnboundPredicate {
        val element: UnboundPredicate
    }

    sealed interface Binding: Subject, Predicate, Object {
        val name: String
    }

    /**
     * Bindings coming directly from an entered query
     */
    @JvmInline
    value class RegularBinding(override val name: String): Binding

    /**
     * Bindings generated by the runtime for use in query rules only. Differ from regular bindings in the way they are
     *  presented back to the user in string form
     */
    @JvmInline
    value class GeneratedBinding(val id: Int): Binding {
        override val name: String get() = " b${id}"
    }

    @JvmInline
    value class Exact(val term: Quad.Term): Subject, UnboundPredicate, Object

    @JvmInline
    value class Alts(val allowed: List<Predicate>): Predicate

    @JvmInline
    value class UnboundAlts(val allowed: List<UnboundPredicate>): UnboundPredicate

    /*
    cannot always be destructured using generated bindings, as they sometimes appear in repeating or inverse structures;
    in the repeating cases, bindings are not allowed, so this version cannot be used
    */
    @JvmInline
    value class Chain(val chain: List<Predicate>): Predicate

    /*
    cannot always be destructured using generated bindings, as they sometimes appear in repeating or inverse structures
    */
    @JvmInline
    value class UnboundChain(val chain: List<UnboundPredicate>): UnboundPredicate

    @JvmInline
    value class UnboundInverse(val predicate: UnboundPredicate): UnboundPredicate

    @JvmInline
    value class ZeroOrMore(override val element: UnboundPredicate): RepeatingPredicate, UnboundPredicate

    @JvmInline
    value class OneOrMore(override val element: UnboundPredicate): RepeatingPredicate, UnboundPredicate

}
