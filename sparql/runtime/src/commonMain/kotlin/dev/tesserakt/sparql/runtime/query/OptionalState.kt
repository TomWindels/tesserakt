package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.query.jointree.from
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Optional
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.sparql.util.OneCardinality
import dev.tesserakt.util.replace

sealed interface OptionalState {

    class AppliedOptionalSingle(
        private val inner: MutableJoinState,
        private val optional: OptionalBlock,
        private val filters: List<FilterExpression>,
    ) : MutableJoinState {

        class OptionalBlock(
            parentBindings: BindingIdentifierSet,
            val state: JoinTree,
        ) {

            data class PeekResult(
                /**
                 * A collection of mappings (with values for [commonBindings]) that only now are being 'enriched' by this
                 *  `OPTIONAL` by applying the delta.
                 */
                val new: List<Mapping>,
                /**
                 * A collection of mappings (with values for [commonBindings]) that are no longer 'enriched' by this
                 *  `OPTIONAL` by applying the delta
                 */
                val removed: List<Mapping>,
                /**
                 * The full set of changes observed by the [state] (which was also used to populate [new] and [removed])
                 */
                val changes: OptimisedStream<MappingDelta>,
            )

            val commonBindings = parentBindings.intersect(state.bindings)

            /**
             * All mappings we've currently 'enriched' with results from our [state].
             * The mappings (key element) are those retained based on the [commonBindings]
             */
            private val affected = Counter<Mapping>()

            init {
                state.join(MappingAddition(BitsetMapping.EMPTY, null)).forEach { delta ->
                    check(delta is MappingAddition)
                    val retained = delta.value.retain(commonBindings)
                    affected.increment(retained)
                }
            }

            fun process(delta: DataDelta) {
                state.peek(delta).forEach { delta ->
                    val retained = delta.value.retain(commonBindings)
                    when (delta) {
                        is MappingAddition -> {
                            affected.increment(retained)
                        }
                        is MappingDeletion -> {
                            affected.decrement(retained)
                        }
                    }
                }
                state.process(delta)
            }

            fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return Statistics.DescriptionElement(
                    description = "OPTIONAL",
                    inner = state.stats(context, granularity),
                )
            }

            fun optionalJoin(stream: Stream<MappingDelta>): Stream<MappingDelta> {
                return stream.transform(state.cardinality.coerceAtLeast(OneCardinality)) { element ->
                    state.optionalJoin(element)
                }
            }

            fun peek(delta: DataDelta): PeekResult {
                // acts as a counter that can go below 0
                val changes = mutableMapOf<Mapping, Pair<Int, ArrayList<MappingDelta>>>()
                state.peek(delta).forEach { delta ->
                    val retained = delta.value.retain(commonBindings)
                    changes.replace(retained) { existing ->
                        val (total, arr) = existing ?: (0 to arrayListOf())
                        when (delta) {
                            is MappingAddition -> {
                                arr.add(delta)
                                (total + 1) to arr
                            }

                            is MappingDeletion -> {
                                arr.add(delta)
                                (total - 1) to arr
                            }
                        }
                    }
                }
                val new = mutableListOf<Mapping>()
                val removed = mutableListOf<Mapping>()
                val result = mutableListOf<MappingDelta>()
                changes.forEach { (mapping, change) ->
                    if (change.first == 0 || change.second.isEmpty()) {
                        return@forEach
                    }
                    val current = affected[mapping]
                    when {
                        current + change.first == 0 -> {
                            check(change.second.all { it is MappingDeletion })
                            // we no longer 'enrich' results that match with this mapping
                            removed.add(mapping)
                            result.addAll(change.second)
                        }
                        current != 0 -> {
                            // we're blocking more / less, so we simply join what has changed on top of the rest
                            result.addAll(change.second)
                        }
                        else -> {
                            check(change.second.all { it is MappingAddition })
                            // we are now freshly replacing (a subset of) the original (inner body's) result
                            new.add(mapping)
                            result.addAll(change.second)
                        }
                    }
                }
                return PeekResult(
                    new = new,
                    removed = removed,
                    changes = result.toStream(),
                )
            }

        }

        override val bindings: BindingIdentifierSet = inner.bindings + optional.state.bindings

        override val cardinality: Cardinality
            // quick estimation w/o doing any actual joins
            get() = inner.cardinality * optional.state.cardinality.coerceAtLeast(OneCardinality)

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return optional
                .optionalJoin(inner.join(delta))
                .filtered { delta -> filters.all { it.test(delta.value) } }
        }

        override fun reindex(
            bindings: BindingIdentifierSet,
            hint: MappingArrayHint
        ) {
            if (bindings !in inner.bindings) {
                return
            }
            inner.reindex(bindings, hint)
        }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            val peekResult = optional.peek(delta)
            if (peekResult.removed.isEmpty() && peekResult.new.isEmpty()) {
                if (peekResult.changes.hasZeroCardinality()) {
                    return optional
                        .optionalJoin(inner.peek(delta))
                        .filtered { delta -> filters.all { it.test(delta.value) } }
                        .optimisedForSingleUse()
                }
                return optional
                    .optionalJoin(inner.peek(delta))
                    .chain(inner.join(peekResult.changes))
                    .filtered { delta -> filters.all { it.test(delta.value) } }
                    .optimisedForSingleUse()
            }
            return optional
                .optionalJoin(inner.peek(delta))
                .chain(inner.join(peekResult.changes))
                .let { stream ->
                    if (peekResult.removed.isNotEmpty()) {
                        stream.chain(inner.join(peekResult.removed.toStream().mapped { MappingAddition(it, delta) }))
                    } else {
                        stream
                    }
                }
                .let { stream ->
                    if (peekResult.new.isNotEmpty()) {
                        stream.chain(inner.join(peekResult.new.toStream().mapped { MappingDeletion(it, delta) }))
                    } else {
                        stream
                    }
                }
                .filtered { delta -> filters.all { it.test(delta.value) } }
                .optimisedForSingleUse()
        }

        override fun process(delta: DataDelta) {
            inner.process(delta)
            optional.process(delta)
        }

        override fun stats(
            context: QueryContext,
            granularity: QueryStatistics.Granularity
        ): Statistics {
            val inner = Statistics.JoinedElement(
                left = inner.stats(context, granularity),
                right = optional.stats(context, granularity)
            )
            if (granularity isAtLeast QueryStatistics.Granularity.DETAILED && filters.isNotEmpty()) {
                return Statistics.DescriptionElement(
                    description = "Filtered\n${filters.joinToString("\n")}",
                    inner = inner,
                )
            }
            return inner
        }

    }

    class AppliedOptionalMultiple(
        private val inner: MutableJoinState,
        private val optionals: List<OptionalBlock>,
        private val filters: List<FilterExpression>,
    ): MutableJoinState {

        class OptionalBlock(
            val state: JoinTree,
        ) {

            fun process(delta: DataDelta) {
                state.process(delta)
            }

            fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return Statistics.DescriptionElement(
                    description = "OPTIONAL",
                    inner = state.stats(context, granularity),
                )
            }

            fun optionalJoin(stream: Stream<MappingDelta>): Stream<MappingDelta> {
                return stream.transform(state.cardinality.coerceAtLeast(OneCardinality)) { element ->
                    state.optionalJoin(element)
                }
            }

            fun optionalJoin(delta: DataDelta, stream: Stream<MappingDelta>): Stream<MappingDelta> {
                val peeked = state.peek(delta)
                return if (peeked.hasZeroCardinality()) {
                    // this data change does not affect us in a meaningful way, so we can do a more direct join
                    stream.transform(state.cardinality.coerceAtLeast(OneCardinality)) { element ->
                        state.optionalJoin(element)
                    }
                } else {
                    // we need to create a 'combined' intermediate state, which only contains mapping 'additions' for
                    //  us to join with
                    val base = state
                        .join(MappingAddition(BitsetMapping.EMPTY, null))
                        .chain(peeked)
                        // we have to 'simplify' these combined results immediately, as otherwise `optionalJoin` might
                        //  misbehave when an addition - deletion combo 'consume' each other and thus incorrectly emit
                        //  no results
                        .simplified()
                    // the result should now only be mapping additions
                    stream.transform(state.cardinality.coerceAtLeast(OneCardinality)) { element ->
                        base.optionalJoin(element)
                    }
                }

            }

            /**
             * Checks to see if different results are generated by applying this [delta]
             */
            fun isInvalidatedBy(delta: DataDelta): Boolean {
                // acts as a counter that can go below 0
                return state.peek(delta).iterator().hasNext()
            }

        }

        override val bindings = optionals
            .fold(inner.bindings) { result, optional -> result + optional.state.bindings }

        private var state = MappingArray(BindingIdentifierSet.EMPTY)

        override val cardinality: Cardinality
            get() = state.cardinality

        init {
            // we have to construct our initial state
            val base: Stream<MappingDelta> = inner
                .join(MappingAddition(BitsetMapping.EMPTY, null))
                // required for our `check` that only expects mapping additions; if we're dealing with a mapping
                //  deletion, it should consume the corresponding additions before we do the optional join chain
                .simplified()
            val final = optionals.fold(base) { stream, optional ->
                optional.optionalJoin(stream)
            }
            // these should all be mapping additions
            final.forEach { delta ->
                check(delta is MappingAddition)
                if (filters.all { it.test(delta.value) }) {
                    state.add(delta.value)
                }
            }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return delta.mapToStream { mapping -> state.join(mapping) }
        }

        override fun reindex(
            bindings: BindingIdentifierSet,
            hint: MappingArrayHint
        ) {
            inner.reindex(bindings, hint)
        }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            // two options: either the delta affects any of our OPTIONAL blocks, in which case we have to re-evaluate
            //  the results, or the delta (at most) only affects our regular inner state, to which we can apply
            //  regular join semantics
            val affectsOptionals = optionals.any { it.isInvalidatedBy(delta) }
            return if (!affectsOptionals) {
                optionals
                    .fold(inner.peek(delta)) { stream, optional -> optional.optionalJoin(stream).optimisedForSingleUse() }
                    .filtered { delta -> filters.all { it.test(delta.value) } }
                    .optimisedForSingleUse()
            } else {
                // we have to recalculate the entire state from nothing, combined with the impact of the peeked change,
                //  and have that ripple through all our optionals
                val newState = stateAfter(delta)
                // our peeked delta is the difference between this new state and our existing state
                val c1 = Counter(state.iter())
                val c2 = Counter(newState.iter())
                val total = c1.current + c2.current
                val diffs = total.associateWith { c2[it] - c1[it] }
                CollectedStream(
                    data = diffs.asIterable().flatMap { (mapping, count) ->
                        when {
                            count == 0 -> emptyList()
                            count > 0 -> {
                                List(count) { MappingAddition(mapping, null) }
                            }
                            else -> {
                                List(-count) { MappingDeletion(mapping, null) }
                            }
                        }
                    }
                )
            }
        }

        override fun process(delta: DataDelta) {
            state = stateAfter(delta)
            inner.process(delta)
            optionals.forEach { it.process(delta) }
        }

        override fun stats(
            context: QueryContext,
            granularity: QueryStatistics.Granularity
        ): Statistics {
            val inner = optionals.fold(inner.stats(context, granularity)) { stats, optional ->
                Statistics.JoinedElement(
                    left = stats,
                    right = optional.stats(context, granularity)
                )
            }
            if (granularity isAtLeast QueryStatistics.Granularity.DETAILED && filters.isNotEmpty()) {
                return Statistics.DescriptionElement(
                    description = "Filtered\n${filters.joinToString("\n")}",
                    inner = inner,
                )
            }
            return inner
        }

        // TODO cache this result based on process call & key
        private fun stateAfter(delta: DataDelta): MappingArray {
            val base: Stream<MappingDelta> = inner
                .join(MappingAddition(BitsetMapping.EMPTY, null))
                .chain(inner.peek(delta))
                // required for our `check` that only expects mapping additions; if we're dealing with a mapping
                //  deletion, it should consume the corresponding additions before we do the optional join chain
                .simplified()
            val final = optionals.fold(base) { stream, optional ->
                optional.optionalJoin(delta, stream)
            }
            // these should all be mapping additions
            // TODO reuse the stored requested index pair / hint here
            val result = MappingArray(BindingIdentifierSet.EMPTY)
            final.forEach { delta ->
                check(delta is MappingAddition)
                if (filters.all { it.test(delta.value) }) {
                    result.add(delta.value)
                }
            }
            return result
        }

    }

    companion object {

        operator fun invoke(
            context: QueryContext,
            inner: MutableJoinState,
            optionals: List<Optional>,
            filters: List<FilterExpression>,
        ): MutableJoinState {
            if (optionals.isEmpty()) {
                return inner
            }
            if (optionals.size == 1) {
                val optional = JoinTree.from(
                    context = context,
                    patterns = optionals[0].patterns,
                    filters = optionals[0].filters.map { filter -> FilterExpression(context, filter.expression) },
                )
                return AppliedOptionalSingle(
                    inner = inner,
                    optional = AppliedOptionalSingle.OptionalBlock(
                        parentBindings = inner.bindings,
                        state = optional,
                    ),
                    // we assume that filters are pushed down already, so we only need to evaluate those that reference
                    //  bindings that come from OPTIONAL blocks
                    filters = filters
                        .filter { filter -> filter.bindings.asIntIterable().any { bindingId -> bindingId in optional.bindings } },
                )
            }
            val optionals = optionals.map { optional ->
                AppliedOptionalMultiple.OptionalBlock(
                    state = JoinTree.from(
                        context = context,
                        patterns = optional.patterns,
                        filters = optional.filters.map { filter -> FilterExpression(context, filter.expression) },
                    )
                )
            }
            return AppliedOptionalMultiple(
                inner = inner,
                optionals = optionals,
                // we assume that filters are pushed down already, so we only need to evaluate those that reference
                //  bindings that come from OPTIONAL blocks
                filters = filters
                    .filter { filter -> filter.bindings.asIntIterable().any { bindingId -> optionals.any { bindingId in it.state.bindings } } },
            )
        }

    }

}

// TODO make this a stream operator, doing it lazily (during first iteration) so we can avoid
//  having this iterator evaluated twice
//  maybe call it `chainIfEmpty`?
private fun JoinTree.optionalJoin(delta: MappingDelta, fallbackElement: MappingDelta = delta): Stream<MappingDelta> {
    // we have to check if we produce at least one result after joining, because if we don't, we
    //  have to emit the original result back instead
    // this can happen when we have UNION segments or multiple OPTIONAL blocks, meaning we don't
    //  have common bindings for direct lookup, but we do still have to satisfy our contract
    val r = this.join(delta)
    if (r.iterator().hasNext()) {
        return r
    }
    return streamOf(fallbackElement)
}


// TODO make this a stream operator, doing it lazily (during first iteration) so we can avoid
//  having this iterator evaluated twice
//  maybe call it `chainIfEmpty`?
private fun Stream<MappingDelta>.optionalJoin(delta: MappingDelta, fallbackElement: MappingDelta = delta): Stream<MappingDelta> {
    // we have to check if we produce at least one result after joining, because if we don't, we
    //  have to emit the original result back instead
    // this can happen when we have UNION segments or multiple OPTIONAL blocks, meaning we don't
    //  have common bindings for direct lookup, but we do still have to satisfy our contract
    val r = join(this, streamOf(delta))
    if (r.iterator().hasNext()) {
        return r
    }
    return streamOf(fallbackElement)
}

private fun Stream<MappingDelta>.simplified(): CollectedStream<MappingDelta> {
    val combined = this
        .groupingBy { it.value }
        .fold({ _, _ -> 0 }) { _, count, delta ->
            val d = if (delta is MappingAddition) 1 else -1
            count + d
        }
    return CollectedStream(
        data = combined.asIterable().flatMap { (mapping, count) ->
            when {
                count == 0 -> emptyList()
                count > 0 -> {
                    List(count) { MappingAddition(mapping, null) }
                }
                else -> {
                    List(-count) { MappingDeletion(mapping, null) }
                }
            }
        }
    )
}
