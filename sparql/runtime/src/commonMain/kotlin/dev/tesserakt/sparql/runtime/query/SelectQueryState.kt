package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.into
import dev.tesserakt.sparql.runtime.query.select.OutputState
import dev.tesserakt.sparql.types.SelectQueryStructure
import dev.tesserakt.sparql.util.MappedCollection.Companion.mapLazily

class SelectQueryState(
    ast: SelectQueryStructure,
    source: Store?,
): QueryState<Bindings, SelectQueryStructure>(ast, source) {

    private val projectionSet = BindingIdentifierSet(context, ast.bindings)

    private val _results = OutputState(context, ast)
    override val results: Collection<BindingsImpl>
        get() = _results.mapLazily { it.into(context) }

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
        val bindings = change.value.retain(projectionSet).into(context)
        return when (change) {
            is MappingAddition -> ResultChange.New(bindings)
            is MappingDeletion -> ResultChange.Removed(bindings)
        }
    }
}
