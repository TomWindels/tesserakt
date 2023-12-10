package tesserakt.sparql.runtime.query

import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.sparql.runtime.types.Bindings

class SelectQuery internal constructor(ast: SelectQueryAST): Query<Bindings, SelectQueryAST>(ast) {

    override fun process(bindings: Bindings): Bindings {
        // TODO: apply aggregations as well
        return bindings.filterKeys { name -> name in ast.output.names }
    }

}
