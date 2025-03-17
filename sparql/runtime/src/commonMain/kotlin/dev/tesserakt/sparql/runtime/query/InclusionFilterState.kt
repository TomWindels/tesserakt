package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.util.compatibleWith
import dev.tesserakt.util.replace

sealed interface InclusionFilterState: MutableFilterState {

    /**
     * Peeks the total impact this filter has when applying the [delta] in this state
     */
    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta>

    /**
     * Filters the [input] stream, using its processed internal state after applying the [delta]
     */
    override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta>

    /**
     * Filters the [input] stream, using only its processed internal state
     */
    override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta>

    override fun process(delta: DataDelta)

    override fun debugInformation(): String

    /**
     * The typical exclude filter, where its internal state affects parts of the results from its parent; those
     *  affected have shared binding names, represented using the [commonBindingNames]
     *  collection (which may not be empty!)
     */
    class Narrow(
        private val commonBindingNames: Set<String>,
        private val state: BasicGraphPatternState,
    ) : InclusionFilterState {

        init {
            require(commonBindingNames.isNotEmpty()) { "Invalid filter use detected!" }
        }

        // tracking what binding groups are "invalid" (= should be filtered out)
        private val filtered = Counter<Mapping>()

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            val changes = state.peek(delta).mapped { it.map { it.retain(commonBindingNames) } }
            // these changes, combined with the `filtered` state, will result in a set of bindings that can now be joined
            //  with to find all resulting changes:
            // * change deletions (in filtered now, but removed in `changes`) => these have to be removed outwards
            // * change additions (not in filtered now, but in `changes`) => these have to be added outwards
            val diff = mutableMapOf<Mapping, Int>()
            changes.forEach { mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> diff.replace(mappingDelta.value) { (it ?: 0) + 1 }
                    is MappingDeletion -> diff.replace(mappingDelta.value) { (it ?: 0) - 1 }
                }
            }
            val results = mutableListOf<MappingDelta>()
            diff.forEach { (mapping, count) ->
                val current = filtered[mapping]
                when {
                    count < 0 && current <= -count -> {
                        // it's now being filtered out, meaning it's removal becomes the result of the peek
                        results.add(MappingDeletion(value = mapping, origin = null))
                    }

                    count > 0 && current == 0 -> {
                        // it's no longer being filtered out, meaning it's addition becomes the result of the peek
                        results.add(MappingAddition(value = mapping, origin = null))
                    }
                }
            }
            return CollectedStream(results)
        }

        /**
         * Filters the [input] stream, using its processed internal state after applying the [delta]
         */
        override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
            // starting from the active state
            val total = filtered.clone()
            // applying the impact of the new delta to it
            state
                .peek(delta)
                .mapped { it.map { it.retain(commonBindingNames) } }
                .forEach { mappingDelta ->
                    when (mappingDelta) {
                        is MappingAddition -> total.increment(mappingDelta.value)
                        is MappingDeletion -> total.decrement(mappingDelta.value)
                    }
                }
            // using that to filter the incoming result
            return input.filtered { mapping -> total.current.any { it.compatibleWith(mapping.value) } }
        }

        /**
         * Filters the [input] stream, using only its processed internal state
         */
        override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
            return input.filtered { mapping -> filtered.current.any { it.compatibleWith(mapping.value) } }
        }

        override fun process(delta: DataDelta) {
            state
                .peek(delta)
                .mapped { it.map { it.retain(commonBindingNames) } }
                .forEach { mappingDelta ->
                    when (mappingDelta) {
                        is MappingAddition -> filtered.increment(mappingDelta.value)
                        is MappingDeletion -> filtered.decrement(mappingDelta.value)
                    }
                }
            state.process(delta)
        }

        override fun debugInformation(): String = buildString {
            appendLine("* Include graph filter (narrow)")
            append(state.debugInformation())
            append("allowing ${filtered.current.size} binding variants: ${filtered.current.joinToString()}")
        }

    }

    /**
     * Special variant of the exclude filter, where the # of common bindings is zero, meaning that a satisfied internal
     *  state means no bindings are coming through
     */
    class Broad(private val state: BasicGraphPatternState) : InclusionFilterState {

        private var count = 0

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            val change = state.peek(delta).fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }
            // if the count becomes > 0 through this delta, all mappings should be added;
            if (count == 0 && change != 0) {
                check(change > 0) { "Invalid internal state!" }
                return streamOf(MappingAddition(emptyMapping(), null))
            }
            // similarly, if the count becomes 0 through this delta, all mappings should be removed
            if (count > 0 && count + change <= 0) {
                check(count + change == 0) { "Invalid internal state!" }
                return streamOf(MappingDeletion(emptyMapping(), null))
            }
            // nothing changed, so the peek is empty
            return emptyStream()
        }

        override fun filter(input: Stream<MappingDelta>): Stream<MappingDelta> {
            return if (count > 0) {
                // everything is allowed
                input
            } else {
                emptyStream()
            }
        }

        override fun filter(input: Stream<MappingDelta>, delta: DataDelta): Stream<MappingDelta> {
            val change = state.peek(delta).fold(0) { acc, mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> acc + 1
                    is MappingDeletion -> acc - 1
                }
            }
            return if (count + change > 0) {
                // everything is allowed
                input
            } else {
                emptyStream()
            }
        }

        override fun process(delta: DataDelta) {
            state.peek(delta).forEach { mappingDelta ->
                when (mappingDelta) {
                    is MappingAddition -> ++count
                    is MappingDeletion -> --count
                }
            }
            state.process(delta)
            check(count >= 0) { "Invalid internal state!" }
        }

        override fun debugInformation(): String = buildString {
            appendLine("* Include graph filter (wide)")
            append(state.debugInformation())
            append("blocking all binding variants: ${count > 0}")
        }

    }

    companion object {

        operator fun invoke(parent: GroupPatternState, filter: Filter.Exists): InclusionFilterState {
            val state = BasicGraphPatternState(filter.pattern)
            val externalBindings = parent.bindings.intersect(state.bindings)
            return if (externalBindings.isEmpty()) {
                Broad(state = state)
            } else {
                Narrow(
                    commonBindingNames = externalBindings,
                    state = state
                )
            }
        }

    }

}
