package tesserakt.sparql.runtime.query

import tesserakt.sparql.runtime.types.Bindings
import tesserakt.sparql.runtime.types.SelectQueryASTr

class SelectQuery internal constructor(ast: SelectQueryASTr): Query<Bindings, SelectQueryASTr>(ast) {

    override fun process(bindings: Bindings): Bindings {
        // TODO: apply aggregations as well
        return bindings.filterKeys { name -> name in ast.output }
    }

}
