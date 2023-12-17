package tesserakt.sparql.runtime.compat

import tesserakt.sparql.compiler.types.QueryAST
import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.sparql.runtime.types.QueryASTr

class QueryCompatLayer: CompatLayer<QueryAST, QueryASTr>() {

    override fun convert(source: QueryAST): QueryASTr = when (source) {
        is SelectQueryAST -> SelectQueryCompatLayer().convert(source)
    }

}
