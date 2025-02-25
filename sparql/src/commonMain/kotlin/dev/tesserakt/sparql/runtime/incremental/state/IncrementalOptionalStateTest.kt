package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.DataAddition
import dev.tesserakt.sparql.runtime.incremental.delta.DataDeletion
import dev.tesserakt.sparql.runtime.incremental.delta.DataDelta
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.types.Counter
import dev.tesserakt.sparql.runtime.incremental.types.Counter.Companion.flatten
import dev.tesserakt.sparql.runtime.incremental.types.Optional
import dev.tesserakt.sparql.runtime.util.getAllBindings
import dev.tesserakt.util.compatibleWith

internal class IncrementalOptionalStateTest(
    private val inner: MutableJoinState,
    ast: Optional
): MutableJoinState {

    private val state = IncrementalPatternsState(ast.patterns)
    // extracting of our own bindings not visible to the parent, required for accurate deltas during `peek()`
    private val invisible: Set<String> = run {
        val ours = ast.patterns.getAllBindings().mapTo(mutableSetOf()) { it.name }
        ours.removeAll(inner.bindings)
        ours
    }

    // tracking the # of unsatisfied [MappingDelta]s we have let through
    private val unsatisfied = Counter<MappingDelta>()

    override val bindings: Set<String> = inner.bindings + state.bindings

    override fun peek(delta: DataDelta): List<MappingDelta> {
        return when (delta) {
            is DataAddition -> {
                val new = state.peek(delta)
                val additions =
                    // the new ones have to be joined with the inner state
                    new.flatMap { inner.join(it) } +
                    // and the deltas of the wrapped state also have to be taken into account
                    // TODO: has to be joined with an expanded mapping stream of the `state` member
                    inner.peek(delta)
                // checking to see if applying the delta would cause prior `unsatisfied` results to be replaced
                val replaced = unsatisfied
                    .filter { (unsatisfied, _) -> new.any { it.value.bindings.compatibleWith(unsatisfied.value) } }
                    .flatten()
                replaced + additions
            }

            is DataDeletion -> {
                val removed = state.peek(delta)
                TODO()
            }
        }
    }

    override fun join(delta: MappingDelta): List<MappingDelta> {
        val first = inner.join(delta)
        return first.flatMap { original ->
            state.join(original).ifEmpty {
                unsatisfied.increment(original)
                listOf(original)
            }
        }
    }

    override fun process(delta: DataDelta) {
        when (delta) {
            is DataAddition -> {
                val new = state.peek(delta)
                // checking to see if applying the delta would cause prior `unsatisfied` results to be replaced
                unsatisfied.clear { unsatisfied -> new.any { it.value.bindings.compatibleWith(unsatisfied.value) } }
            }
            is DataDeletion -> TODO()
        }
        inner.process(delta)
        state.process(delta)
    }

    companion object {

        fun from(inner: MutableJoinState, optionals: List<Optional>): MutableJoinState {
            var result = inner
            optionals.forEach { optional ->
                result = IncrementalOptionalStateTest(result, optional)
            }
            return result
        }

    }

}
