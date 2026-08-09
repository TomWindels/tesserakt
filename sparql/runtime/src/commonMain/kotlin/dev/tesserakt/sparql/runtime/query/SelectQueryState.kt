package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.asResultChange
import dev.tesserakt.sparql.runtime.query.QueryState.ResultChange.Companion.into
import dev.tesserakt.sparql.runtime.query.select.OutputState
import dev.tesserakt.sparql.runtime.stream.CollectedStream
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
            // making sure deletions and additions consume each other as much as possible first;
            // without this, it becomes possible for a deletion to happen before its addition took place
            .simplified()
            .onEach(::onNewBodyResult)
            .map { it.asResultChange(context) }
    }

    override fun process(data: DataDelta) {
        bgpState
            .insert(data)
            // making sure deletions and additions consume each other as much as possible first;
            // without this, it becomes possible for a deletion to happen before its addition took place
            .simplified()
            .forEach(::onNewBodyResult)
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

    /**
     * Simplifies a stream of deltas, removing opposing changes, potentially reducing the total number of elements.
     */
    // TODO perf:
    //  simply return another Iterable, so we don't need to create a potentially big array at the end
    private fun Iterable<MappingDelta>.simplified(): List<MappingDelta> {
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

}
