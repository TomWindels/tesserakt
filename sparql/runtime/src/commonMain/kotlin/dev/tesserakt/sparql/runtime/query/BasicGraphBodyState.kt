package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.query.jointree.from
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.types.Optional
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import kotlin.jvm.JvmInline

@JvmInline
value class BasicGraphBodyState private constructor(
    private val inner: MutableJoinState,
): MutableJoinState by inner {

    companion object {

        operator fun invoke(
            context: QueryContext,
            statements: List<GraphPattern.Statement>,
            filters: List<FilterExpression>,
            externalBindings: BindingIdentifierSet,
        ): MutableJoinState {
            // TODO: optimize the statements order by delaying optional evaluation as much as possible:
            //  an optional can be pushed down until encountering another element producing a binding name
            //  that was introduced by that optional element
            val statements = statements
            // we need to construct a single join state from the collection of statements we've been given:
            //  the order of operations matters when dealing with OPTIONAL blocks (or unions that contain OPTIONAL
            //  blocks)
            val states = mutableListOf<MutableJoinState>()
            var i = 0
            while (i < statements.size) {
                when (val statement = statements[i]) {
                    is TriplePattern -> {
                        var state = TriplePatternState.from(
                            context = context,
                            pattern = statement,
                        )
                        filters.forEach { filter ->
                            if (filter.bindings in state.bindings) {
                                state = state.filtered(filter)
                            }
                        }
                        state.prefill()
                        states.add(state)
                    }
                    is Union -> {
                        val state = UnionState(
                            context = context,
                            union = statement,
                            filters = filters,
                        )
                        states.add(state)
                    }
                    is Optional -> {
                        val optionalFilters = statement.filters.map {
                            FilterExpression(context, it.expression)
                        }
                        val optionalBody = statement.patterns.map { pattern ->
                            var state = TriplePatternState.from(
                                context = context,
                                pattern = pattern,
                            )
                            optionalFilters.forEach { filter ->
                                if (filter.bindings in state.bindings) {
                                    state = state.filtered(filter)
                                }
                            }
                            state.prefill()
                            state
                        }
                        val optionalBlock = JoinTree.from(
                            context = context,
                            states = optionalBody,
                            filters = optionalFilters,
                            externalBindings = states.fold(BindingIdentifierSet.EMPTY) { total, state ->
                                total + state.bindings
                            },
                        )
                        // we have to consume all prior states now, as this is an order-dependant operation
                        val inner = JoinTree.from(
                            context = context,
                            states = states,
                            filters = filters,
                            externalBindings = optionalBlock.bindings
                        )

                        val optional = OptionalState(
                            inner = inner,
                            optional = optionalBlock,
                            filters = filters,
                        )
                        // we just consumed all prior states with our optional segment
                        states.clear()
                        // so the optional becomes the top most (and only) segment
                        states.add(optional)
                    }
                }
                ++i
            }

            return BasicGraphBodyState(
                inner = JoinTree.from(
                    context = context,
                    states = states,
                    filters = filters,
                    externalBindings = externalBindings
                ),
            )
        }

    }

}
