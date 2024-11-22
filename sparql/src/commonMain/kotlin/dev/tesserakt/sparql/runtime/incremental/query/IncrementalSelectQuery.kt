package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuery

class IncrementalSelectQuery internal constructor(ast: SelectQuery): IncrementalQuery<Bindings, SelectQuery>(ast) {

    private val output = ast.output.mapTo(mutableSetOf()) { it.name }

    override fun process(change: ResultChange): Bindings {
        return when (change) {
            is ResultChange.New -> change.bindings.filterKeys { name -> name in output }
            is ResultChange.Removed -> TODO()
        }
    }

}
