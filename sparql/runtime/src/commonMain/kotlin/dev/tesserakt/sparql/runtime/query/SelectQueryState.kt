package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.types.SelectQueryStructure
import dev.tesserakt.util.associateWithNotNull

class SelectQueryState(ast: SelectQueryStructure): QueryState<Bindings, SelectQueryStructure>(ast) {

    val variables = ast.bindings

    override fun process(change: ResultChange<Bindings>): ResultChange<Bindings> {
        return when (change) {
            is ResultChange.New -> ResultChange.New(variables.associateWithNotNull { change.value[it] })
            is ResultChange.Removed -> ResultChange.Removed(variables.associateWithNotNull { change.value[it] })
        }
    }

}
