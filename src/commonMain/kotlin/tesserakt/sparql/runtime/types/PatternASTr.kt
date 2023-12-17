package tesserakt.sparql.runtime.types

import tesserakt.rdf.types.Triple
import kotlin.jvm.JvmInline

data class PatternASTr(
    val s: Subject,
    val p: Predicate,
    val o: Object,
): ASTr {

    sealed interface Subject: ASTr

    sealed interface Predicate: ASTr

    sealed interface Object: ASTr

    sealed interface NonRepeatingPredicate: Predicate

    sealed interface FixedPredicate: NonRepeatingPredicate

    sealed interface RepeatingPredicate: Predicate

    sealed interface Binding: Subject, NonRepeatingPredicate, Object {
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
    value class Exact(val term: Triple.Term): Subject, FixedPredicate, Object

    @JvmInline
    value class Alts(val allowed: List<FixedPredicate>): FixedPredicate

    @JvmInline
    value class Inverse(val predicate: FixedPredicate): FixedPredicate

    @JvmInline
    value class ZeroOrMoreBound(val predicate: RegularBinding): RepeatingPredicate

    @JvmInline
    value class ZeroOrMoreFixed(val predicate: FixedPredicate): RepeatingPredicate

    @JvmInline
    value class OneOrMoreBound(val predicate: RegularBinding): RepeatingPredicate

    @JvmInline
    value class OneOrMoreFixed(val predicate: FixedPredicate): RepeatingPredicate

}
