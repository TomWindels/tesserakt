package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.jointree.JoinTree
import dev.tesserakt.sparql.runtime.query.jointree.from
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.types.Optional
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.getAllNamedBindings
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
            val statements = statements.withOptimizedOrder(context)
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
                            if (filter.bindings in state.properties.maximum) {
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
                            externalBindings = statements.indices.fold(BindingIdentifierSet.EMPTY) { total, j ->
                                if (i == j) {
                                    return@fold total
                                }
                                // NOTE:
                                //  even though this only captures the *named* bindings, not the generated ones,
                                //  we cannot have generated bindings in common as these are in separate scopes, and
                                //  thus could not have such a binding in common
                                val current = BindingIdentifierSet(context, statements[j].getAllNamedBindings().mapTo(mutableSetOf()) { it.name })
                                total + current
                            },
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
                                if (filter.bindings in state.properties.maximum) {
                                    state = state.filtered(filter)
                                }
                            }
                            state.prefill()
                            state
                        }
                        val optionalBlock = JoinTree.from(
                            states = optionalBody,
                            filters = optionalFilters,
                            externalBindings = states.fold(BindingIdentifierSet.EMPTY) { total, state ->
                                total + state.properties.maximum
                            },
                        )
                        // we have to consume all prior states now, as this is an order-dependant operation
                        val inner = JoinTree.from(
                            states = states,
                            filters = filters,
                            externalBindings = optionalBlock.properties.maximum
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
                    states = states,
                    filters = filters,
                    externalBindings = externalBindings
                ),
            )
        }

        /**
         * Reorders the set of statements so that more expensive statements are delayed as much as possible without affecting
         *  the query correctness.
         */
        private fun List<GraphPattern.Statement>.withOptimizedOrder(context: QueryContext): List<GraphPattern.Statement> {
            // we can delay the evaluation of `OPTIONAL`s until they emit *new* bindings that are about to be referenced
            //  by another state.
            // Example:
            // ```
            // ?s a :Type .
            // OPTIONAL {
            //     ?s ?p ?o .
            // }
            // ...
            // ```
            // The optional can be pushed down until another statement referencing `?p` and/or `?o` is encountered, as `?s` is
            //  already constrained by the first TP

            val result = this.toMutableList()
            fun swap(a: Int, b: Int) {
                val ele = result[a]
                result[a] = result[b]
                result[b] = ele
            }
            var i = 0
            var encountered = BindingIdentifierSet.EMPTY
            while (i < size - 1) {
                // we don't have to continue if all remaining states are already `OPTIONAL`s
                if ((i ..< size).all { result[it] is Optional }) {
                    break
                }
                when (val statement = result[i]) {
                    is Optional -> {
                        // we have to see what bindings it 'introduces'
                        // we only care for named bindings are those are the only ones that can be
                        //  referenced in other statements
                        val total = BindingIdentifierSet(context, statement.getAllNamedBindings().map { it.name })
                        val introduced = total - encountered
                        var j = i + 1
                        // if none of the introduced bindings from this optional are referenced in the next statement,
                        //  we can delay its evaluation
                        // in case we were able to move this block, we need to re-check this index, as another statement
                        //  would be positioned there
                        var moved = false
                        while (j < size) {
                            val neighbour = result[j]
                            val references = BindingIdentifierSet(context, neighbour.getAllNamedBindings().map { it.name })
                            if (introduced.intersectSize(references) != 0) {
                                // we can't let it go down further, there's at least one binding
                                //  from the neighbour element that we have in common with the bindings
                                //  we introduce in the `OPTIONAL` block
                                break
                            }
                            moved = true
                            // we can safely reorder these two statements
                            swap(j - 1, j)
                            ++j
                        }
                        if (!moved) {
                            // we have left it here, so we mark these bindings as encountered
                            encountered += BindingIdentifierSet(context, statement.getAllNamedBindings().map { it.name })
                            ++i
                        }
                    }
                    is TriplePattern,
                    is Union -> {
                        // we can mark these bindings as 'encountered' now
                        encountered += BindingIdentifierSet(context, statement.getAllNamedBindings().map { it.name })
                        ++i
                    }
                }
            }
            return result

//
//            val result = mutableListOf<GraphPattern.Statement>()
//            var i = 0
//            var encountered = BindingIdentifierSet.EMPTY
//            while (i < size) {
//                when (val statement = this[i]) {
//                    is Optional -> {
//                        // we have to see what bindings it 'introduces'
//                        // we only care for named bindings are those are the only ones that can be
//                        //  referenced in other statements
//                        val total = BindingIdentifierSet(context, statement.getAllNamedBindings().map { it.name })
//                        val missing = total - encountered
//                        // if we aren't missing any, we don't even have to check what statement to put it b
//                    }
//                    is TriplePattern,
//                    is Union -> {
//                        result.add(statement)
//                        // we can mark these bindings as 'encountered' now
//                        encountered += BindingIdentifierSet(context, statement.getAllNamedBindings().map { it.name })
//                        ++i
//                    }
//                }
//            }
//            return result
        }
    }

}
