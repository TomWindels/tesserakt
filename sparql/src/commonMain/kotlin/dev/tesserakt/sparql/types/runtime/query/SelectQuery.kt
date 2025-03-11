package dev.tesserakt.sparql.types.runtime.query

import dev.tesserakt.sparql.types.runtime.element.SelectQuery
import dev.tesserakt.sparql.types.runtime.evaluation.Bindings
import dev.tesserakt.util.associateWithNotNull

class SelectQuery internal constructor(ast: SelectQuery): Query<Bindings, SelectQuery>(ast) {

    val variables = ast.output.mapTo(mutableSetOf()) { it.name }.toSet()

    override fun process(change: ResultChange<Bindings>): ResultChange<Bindings> {
        return when (change) {
            is ResultChange.New -> ResultChange.New(variables.associateWithNotNull { change.value[it] })
            is ResultChange.Removed -> ResultChange.Removed(variables.associateWithNotNull { change.value[it] })
        }
    }

}
