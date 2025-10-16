package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.BindingsImpl
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingAddition
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.into
import dev.tesserakt.sparql.types.SelectQueryStructure
import dev.tesserakt.sparql.util.Counter

class SelectQueryState(ast: SelectQueryStructure): QueryState<BindingsImpl, SelectQueryStructure>(ast) {

    val variables = ast.bindings

    private val _results = Counter<BindingsImpl>()

    override val results: Collection<BindingsImpl>
        get() = _results.flatten()

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

    override fun processAndGet(data: DataDelta): List<ResultChange<BindingsImpl>> {
        return bgpState.insert(data)
            .map { it.into(context) }
            .onEach(::onNewBodyResult)
    }

    override fun process(data: DataDelta) {
        bgpState.insert(data).forEach(::onNewBodyResult)
    }

    private inline fun onNewBodyResult(result: MappingDelta) = onNewBodyResult(result.into(context))

    private inline fun onNewBodyResult(result: ResultChange<BindingsImpl>) {
        val projected = applyProjection(result)
        insert(projected)
    }

    private inline fun applyProjection(change: ResultChange<BindingsImpl>): ResultChange<BindingsImpl> {
        return when (change) {
            is ResultChange.New -> ResultChange.New(change.value.retain(variables))
            is ResultChange.Removed -> ResultChange.Removed(change.value.retain(variables))
        }
    }

    private fun insert(change: ResultChange<BindingsImpl>) {
        when (change) {
            is ResultChange.New<*> -> _results.increment(change.value)
            is ResultChange.Removed<*> -> _results.decrement(change.value)
        }
    }

}
