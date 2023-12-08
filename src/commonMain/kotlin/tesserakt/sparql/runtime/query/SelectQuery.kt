package tesserakt.sparql.runtime.query

import tesserakt.sparql.compiler.types.SelectQueryAST

class SelectQuery(ast: SelectQueryAST): Query<Map<String, Any>, SelectQueryAST>(ast) {

    override fun process(bindings: Bindings): Map<String, Any> {
        // TODO: apply aggregations as well
        return bindings.bindings.filterKeys { name -> name in ast.output.names }
    }

}
