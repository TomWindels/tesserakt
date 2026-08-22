package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.materialize
import dev.tesserakt.sparql.runtime.query.select.OutputState
import dev.tesserakt.sparql.types.SelectQueryStructure
import dev.tesserakt.sparql.util.MappedCollection.Companion.mapLazily

class SelectQueryState(
    ast: SelectQueryStructure,
    source: Store?,
): QueryState<Bindings, SelectQueryStructure>(ast, source) {

    private val projectionSet = BindingIdentifierSet(context, ast.bindings)
    private val finalBindingExpressions = ast.body.bindingStatements.map { statement ->
        // TODO:
        //  * skip expressions not visible after projection
        //  * skip inlined expressions
        BindingExpression(context, statement)
    }

    private val _results = OutputState(context, ast)
    override val results: Collection<BindingsImpl>
        get() = _results.mapLazily { it.materialize(context, finalBindingExpressions) }

    init {
        constructInitialState()
    }

    override fun onNewBodyResult(change: MappingDelta) {
        val projected = change.value.retain(projectionSet)
        when (change) {
            is MappingAddition -> {
                _results.onResultAdded(projected)
            }
            is MappingDeletion -> {
                _results.onResultRemoved(projected)
            }
        }
    }

    override fun transformNewBodyResult(change: MappingDelta): ResultChange<Bindings> {
        // TODO: make sure we retain those that are required for the binding expression evaluation first,
        //  retain again after if necessary
        val bindings = change.value.retain(projectionSet).materialize(context, finalBindingExpressions)
        return when (change) {
            is MappingAddition -> ResultChange.New(bindings)
            is MappingDeletion -> ResultChange.Removed(bindings)
        }
    }

    override fun stats(granularity: QueryStatistics.Granularity): Statistics {
        val inner = super.stats(granularity)
        val description = if (
            granularity isAtLeast QueryStatistics.Granularity.DETAILED &&
            finalBindingExpressions.isNotEmpty()
        ) {
            val expr = finalBindingExpressions.joinToString(separator = "\n")
            "SELECT\nBindings ${projectionSet.asIntIterable().joinToString()}\n${expr}"
        } else {
            "SELECT\nBindings ${projectionSet.asIntIterable().joinToString()}"
        }
        return Statistics.DescriptionElement(
            description = description,
            inner = inner
        )
    }

}
