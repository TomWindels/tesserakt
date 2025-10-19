package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.asResultChange
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.into
import dev.tesserakt.sparql.runtime.query.select.OutputState
import dev.tesserakt.sparql.types.SelectQueryStructure
import dev.tesserakt.sparql.util.MappedCollection.Companion.mapLazily

class SelectQueryState(ast: SelectQueryStructure): QueryState<Bindings, SelectQueryStructure>(ast) {

    private val projectionSet = BindingIdentifierSet(context, ast.bindings)

    private val _results = OutputState(context, ast)
    override val results: Collection<BindingsImpl>
        get() = _results.mapLazily { it.into(context) }

    init {
         // required when setting up the initial state: sets up initial state
         //  combinations (i.e. triple patterns such as "?a <p>* <b>", yielding ?a = <b>)
        bgpState
            // getting all current results by joining with an empty new mapping
            .join(
                MappingAddition(
                    value = context.emptyMapping(),
                    origin = null
                )
            )
            .forEach(::onNewBodyResult)
    }

    override fun processAndGet(data: DataDelta): List<ResultChange<Bindings>> {
        return bgpState.insert(data)
            .onEach(::onNewBodyResult)
            .map { it.asResultChange(context) }
    }

    override fun process(data: DataDelta) {
        bgpState.insert(data).forEach(::onNewBodyResult)
    }

    private inline fun onNewBodyResult(result: MappingDelta) {
        val projected = applyProjection(result.asResultChange())
        insert(projected)
    }

    private inline fun applyProjection(change: ResultChange<Mapping>): ResultChange<Mapping> {
        return when (change) {
            is ResultChange.New -> ResultChange.New(change.value.retain(projectionSet))
            is ResultChange.Removed -> ResultChange.Removed(change.value.retain(projectionSet))
        }
    }

    private fun insert(change: ResultChange<Mapping>) {
        when (change) {
            is ResultChange.New<*> -> _results.onResultAdded(change.value)
            is ResultChange.Removed<*> -> _results.onResultRemoved(change.value)
        }
    }

}
