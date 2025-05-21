package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.TriplePatternState
import dev.tesserakt.sparql.runtime.query.UnionState
import dev.tesserakt.sparql.runtime.query.expandBindingDeltas
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.toStream
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Bitmask
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.OneCardinality
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName


/**
 * A join tree that does not contain any state of its own; it simply wraps the [states] into a single join state
 */
// TODO(perf): proper stream use
@JvmInline
value class StatelessJoinTree<J: MutableJoinState>(private val states: List<J>): JoinTree {

    override val bindings: Set<String>
        get() = states.flatMapTo(mutableSetOf()) { it.bindings }

    override val cardinality: Cardinality
        get() = states.fold(OneCardinality) { acc, state -> acc * state.cardinality }

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
        return join(completed = Bitmask.wrap(0, length = states.size), delta).toStream()
    }

    override fun debugInformation() = buildString {
        appendLine(" * Join tree statistics (None)")
        states.forEach { state ->
            appendLine("\t || $state")
        }
    }

    fun join(completed: Bitmask, delta: MappingDelta): List<MappingDelta> {
        if (completed.isOne()) {
            return listOf(delta)
        }
        // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
        //  before iterating over it
        return when (delta) {
            is MappingAddition -> {
                var results: List<MappingDelta> = listOf(delta)
                completed.inv().forEach { i ->
                    results = results.flatMap { states[i].join(it) }
                    if (results.isEmpty()) {
                        return emptyList()
                    }
                }
                results
            }

            is MappingDeletion -> {
                var results: List<MappingDelta> = listOf(delta)
                completed.inv().forEach { i ->
                    results = results.flatMap { states[i].join(it) }
                    if (results.isEmpty()) {
                        return emptyList()
                    }
                }
                results
            }
        }
    }

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(context: QueryContext, patterns: List<TriplePattern>) = StatelessJoinTree(
            states = patterns.map { TriplePatternState.from(context, it) }
        )

        @JvmName("forUnions")
        operator fun invoke(context: QueryContext, unions: List<Union>) = StatelessJoinTree(
            states = unions.map { UnionState(context, it) }
        )

    }

}
