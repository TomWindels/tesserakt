package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.types.Bindings
import dev.tesserakt.sparql.runtime.types.SelectQueryASTr

class SelectQuery internal constructor(ast: SelectQueryASTr): Query<Bindings, SelectQueryASTr>(ast) {

    override fun process(bindings: Bindings): Bindings {
        // TODO: apply aggregations as well
        return bindings.filterKeys { name -> name in ast.output }
    }

}
