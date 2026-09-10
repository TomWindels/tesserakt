package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.mappedNonNull
import dev.tesserakt.sparql.runtime.stream.product

fun join(a: Stream<MappingDelta>, b: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

fun join(a: OptimisedStream<MappingDelta>, b: Stream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

fun join(a: OptimisedStream<MappingDelta>, b: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
    a.product(b).mappedNonNull { (a, b) -> a + b }

inline fun bindingIdentifierSetOf(
    subject: TriplePatternState.Subject,
    predicate: TriplePatternState.Predicate,
    `object`: TriplePatternState.Object
): BindingIdentifierSet = BindingIdentifierSet(
    ids = listOfNotNull(subject.bindingId, predicate.bindingId, `object`.bindingId)
)

inline fun bindingIdentifierSetOf(
    vararg bindingId: BindingIdentifier?
): BindingIdentifierSet = BindingIdentifierSet(
    ids = bindingId.mapNotNull { it?.id }
)

val TriplePatternState.Subject.bindingId: Int?
    get() = (this as? TriplePatternState.Binding)?.id?.id

val TriplePatternState.Predicate.bindingId: Int?
    get() = (this as? TriplePatternState.Binding)?.id?.id

val TriplePatternState.Object.bindingId: Int?
    get() = (this as? TriplePatternState.Binding)?.id?.id

val TriplePatternState.Subject.termId: Int?
    get() = (this as? TriplePatternState.Exact)?.id?.id

val TriplePatternState.Predicate.termId: Int?
    get() = (this as? TriplePatternState.Exact)?.id?.id

val TriplePatternState.Object.termId: Int?
    get() = (this as? TriplePatternState.Exact)?.id?.id


inline fun TriplePatternState.UnboundSequence.unfold(
    context: QueryContext,
    start: TriplePatternState.Subject,
    end: TriplePatternState.Object,
): List<TriplePatternState<*>> {
    require(chain.size >= 2)
    val result = ArrayList<TriplePatternState<*>>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        result.add(TriplePatternState.from(context, subj, p, obj))
        subj = obj.asSubject()
    }
    result.add(TriplePatternState.from(context, subj, chain.last(), end))
    return result
}

inline fun TriplePatternState.Sequence.unfold(
    context: QueryContext,
    start: TriplePatternState.Subject,
    end: TriplePatternState.Object
): List<TriplePatternState<*>> {
    require(chain.size >= 2)
    val result = ArrayList<TriplePatternState<*>>(chain.size)
    var subj = start
    (0 until chain.size - 1).forEach { i ->
        val p = chain[i]
        val obj = TriplePatternState.Binding(BindingIdentifier(context.newAnonymousBinding()))
        result.add(TriplePatternState.from(context, subj, p, obj))
        subj = obj.asSubject()
    }
    result.add(TriplePatternState.from(context, subj, chain.last(), end))
    return result
}

fun TriplePatternState.Object.asSubject(): TriplePatternState.Subject = when (this) {
    is TriplePatternState.Binding -> this
    is TriplePatternState.Exact -> this
}

fun TriplePatternState.Subject.matches(term: TermIdentifier): Boolean =
    (this !is TriplePatternState.Exact || this.id == term)

fun TriplePatternState.Object.matches(term: TermIdentifier): Boolean =
    (this !is TriplePatternState.Exact || this.id == term)

fun TriplePatternState.Exact.matches(term: TermIdentifier): Boolean =
    term == this.id

fun TriplePatternState.Predicate.matches(term: Int): Boolean = when (this) {
    /* all of these contain a binding, so automatically, it matches any term */
    is TriplePatternState.Binding -> true
    is TriplePatternState.Alts -> true
    /* all of these match only a subset of terms, so checking manually */
    is TriplePatternState.Exact -> term == this.id.id
    is TriplePatternState.SimpleAlts -> allowed.any { it.matches(term) }
    is TriplePatternState.Negated -> !terms.matches(term)
    /* these cannot be directly matched to terms, so bailing */
    is TriplePatternState.Sequence,
    is TriplePatternState.UnboundSequence -> throw IllegalArgumentException("Sequences cannot be directly matched with terms!")
    is TriplePatternState.OneOrMore,
    is TriplePatternState.ZeroOrMore -> throw IllegalArgumentException("Repeating patterns cannot be directly matched with terms!")
}
