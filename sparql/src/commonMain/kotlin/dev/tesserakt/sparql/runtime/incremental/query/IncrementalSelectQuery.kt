package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuery

class IncrementalSelectQuery internal constructor(ast: SelectQuery): IncrementalQuery<Bindings, SelectQuery>(ast) {

    override fun process(bindings: Bindings): Bindings {
        val output = ast.output.map { it.name }.toSet()
        return bindings.filterKeys { name -> name in output }
    }

}
