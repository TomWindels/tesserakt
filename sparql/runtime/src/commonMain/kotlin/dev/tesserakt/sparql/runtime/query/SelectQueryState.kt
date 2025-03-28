package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.BindingsImpl
import dev.tesserakt.sparql.types.SelectQueryStructure

class SelectQueryState(ast: SelectQueryStructure): QueryState<BindingsImpl, SelectQueryStructure>(ast) {

    val variables = ast.bindings

    override fun process(change: ResultChange<BindingsImpl>): ResultChange<BindingsImpl> {
        return when (change) {
            is ResultChange.New -> ResultChange.New(change.value.retain(variables))
            is ResultChange.Removed -> ResultChange.Removed(change.value.retain(variables))
        }
    }

}
