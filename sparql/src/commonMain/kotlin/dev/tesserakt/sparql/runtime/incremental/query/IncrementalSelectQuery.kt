package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuery
import dev.tesserakt.util.associateWithNotNull

class IncrementalSelectQuery internal constructor(ast: SelectQuery): IncrementalQuery<Bindings, SelectQuery>(ast) {

    val variables = ast.output.mapTo(mutableSetOf()) { it.name }.toSet()

    override fun process(change: ResultChange<Bindings>): ResultChange<Bindings> {
        return when (change) {
            is ResultChange.New -> ResultChange.New(variables.associateWithNotNull { change.value[it] })
            is ResultChange.Removed -> ResultChange.Removed(variables.associateWithNotNull { change.value[it] })
        }
    }

}
