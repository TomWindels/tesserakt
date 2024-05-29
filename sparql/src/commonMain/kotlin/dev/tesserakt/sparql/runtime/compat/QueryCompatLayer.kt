package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.ast.SelectQueryAST
import dev.tesserakt.sparql.runtime.types.QueryASTr

class QueryCompatLayer: CompatLayer<QueryAST, QueryASTr>() {

    override fun convert(source: QueryAST): QueryASTr = when (source) {
        is SelectQueryAST -> SelectQueryCompatLayer().convert(source)
    }

}
