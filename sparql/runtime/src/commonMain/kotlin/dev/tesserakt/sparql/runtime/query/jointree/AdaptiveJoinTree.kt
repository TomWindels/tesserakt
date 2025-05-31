package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.TriplePatternState
import dev.tesserakt.sparql.runtime.query.UnionState
import dev.tesserakt.sparql.runtime.query.expandBindingDeltas
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Bitmask
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.sparql.util.OneCardinality
import kotlin.jvm.JvmName

class AdaptiveJoinTree(private val context: QueryContext, private val states: List<MutableJoinState>) : JoinTree {

    override val bindings: Set<String>
        get() = states.flatMapTo(mutableSetOf()) { it.bindings }

    override val cardinality: Cardinality
        get() = states.fold(OneCardinality) { acc, state -> acc * state.cardinality }

    init {
        // requesting all inner states to rehash on what other states have in common
        val totalBindings = Counter(states.flatMap { it.bindings })
        states.forEach { state ->
//            val binding = state.bindings.maxByOrNull { totalBindings[it] }?.takeIf { totalBindings[it] > 1 }
//            val set = BindingIdentifierSet(context, listOfNotNull(binding))
            val set = BindingIdentifierSet(context, state.bindings.filter { totalBindings[it] > 1 })
            state.rehash(set)
        }
    }

    override fun rehash(bindings: BindingIdentifierSet) {
        // nothing to do
    }

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        val deltas = states
            .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta).toList() }
            .expandBindingDeltas()
            .flatMap { (completed, delta) -> delta.flatMap { join(completed, it) } }
        return deltas.toStream()
    }

    override fun process(delta: DataDelta) {
        states.forEach { it.process(delta) }
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return join(completed = Bitmask.wrap(0, length = states.size), delta)
    }

    override fun debugInformation() = buildString {
        appendLine(" * Join tree statistics (Adaptive)")
        states.forEach { state ->
            appendLine("\t || $state")
        }
    }

    fun join(completed: Bitmask, delta: MappingDelta): Stream<MappingDelta> {
        if (completed.isOne()) {
            return streamOf(delta)
        }
        // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
        //  before iterating over it
        var results: OptimisedStream<MappingDelta> = streamOf(delta)
        var remaining = completed.inv()
        while (!remaining.isZero()) {
//            val (selected, new) = remaining.minOfBy(
//                comparison = { it.second.cardinality }
//            ) {
//                it to states[it].join(results)
//            }
            val selected= remaining.first()
            val new = states[selected].join(results)
            remaining = remaining.remove(selected)
            results = new.optimisedForSingleUse()
        }
        return results
    }

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(context: QueryContext, patterns: List<TriplePattern>) = AdaptiveJoinTree(
            context = context,
            states = patterns.map { TriplePatternState.from(context, it) }
        )

        @JvmName("forUnions")
        operator fun invoke(context: QueryContext, unions: List<Union>) = AdaptiveJoinTree(
            context = context,
            states = unions.map { UnionState(context, it) }
        )

    }

}

private inline fun <T, R, C: Comparable<C>> Iterable<T>.minOfBy(comparison: (R) -> C, transform: (T) -> R): R {
    val iter = iterator()
    var result = transform(iter.next())
    var value = comparison(result)
    while (iter.hasNext()) {
        val current = transform(iter.next())
        val currentValue = comparison(result)
        if (currentValue < value) {
            value = currentValue
            result = current
        }
    }
    return result
}
