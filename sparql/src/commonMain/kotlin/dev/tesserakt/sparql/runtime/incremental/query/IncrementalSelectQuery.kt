package dev.tesserakt.sparql.runtime.incremental.query

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.SelectQuery

class IncrementalSelectQuery internal constructor(ast: SelectQuery): IncrementalQuery<Bindings, SelectQuery>(ast) {

    val variables = ast.output.mapTo(mutableSetOf()) { it.name }.toSet()

    override fun process(bindings: Bindings): Bindings {
        return bindings.filterKeys { name -> name in variables }
    }

}
