package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.types.TriplePattern
import kotlin.jvm.JvmInline

object TriplePatternElement {

    sealed interface Subject

    sealed interface Predicate

    sealed interface Object

    sealed interface SimplePredicate : Predicate

    @JvmInline
    value class Binding(val binding: BindingIdentifier): Subject, Predicate, Object

    @JvmInline
    value class Exact(val term: TermIdentifier.Materialized): Subject, SimplePredicate, Object

    @JvmInline
    value class Negated(val inner: SimplePredicate): SimplePredicate

    @JvmInline
    value class SimpleAlts(val allowed: List<SimplePredicate>): SimplePredicate

    @JvmInline
    value class Alts(val allowed: List<Predicate>): Predicate

    @JvmInline
    value class Sequence(val chain: List<Predicate>): Predicate

    @JvmInline
    value class ZeroOrMore(val element: Predicate): Predicate

    @JvmInline
    value class OneOrMore(val element: Predicate): Predicate

    @JvmInline
    value class UnboundSequence(val chain: List<Predicate>): Predicate

    ///

    fun transform(context: QueryContext, subject: TriplePattern.Subject): Subject = when (subject) {
        is TriplePattern.Binding -> Binding(binding = BindingIdentifier(context, subject.name))
        is TriplePattern.Exact -> Exact(TermIdentifier.Materialized(context, subject.term))
    }

    fun transform(context: QueryContext, predicate: TriplePattern.Predicate): Predicate = when (predicate) {
        is TriplePattern.Binding -> Binding(binding = BindingIdentifier(context, predicate.name))
        is TriplePattern.Exact -> Exact(TermIdentifier.Materialized(context, predicate.term))
        is TriplePattern.SimpleAlts -> SimpleAlts(predicate.allowed.map { transform(context, it) as SimplePredicate })
        is TriplePattern.Sequence -> Sequence(predicate.chain.map { transform(context, it) })
        is TriplePattern.Negated -> Negated(transform(context, predicate.terms) as SimplePredicate)
        is TriplePattern.Alts -> Alts(predicate.allowed.map { transform(context, it) })
        is TriplePattern.ZeroOrMore -> ZeroOrMore(transform(context, predicate.element))
        is TriplePattern.OneOrMore -> OneOrMore(transform(context, predicate.element))
        is TriplePattern.UnboundSequence -> UnboundSequence(predicate.chain.map { transform(context, it) })
    }

    fun transform(context: QueryContext, `object`: TriplePattern.Object): Object = when (`object`) {
        is TriplePattern.Binding -> Binding(binding = BindingIdentifier(context, `object`.name))
        is TriplePattern.Exact -> Exact(TermIdentifier.Materialized(context, `object`.term))
    }

}
