package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.sparql.compiler.ast.QueryAST
import dev.tesserakt.sparql.compiler.ast.SelectQueryAST
import dev.tesserakt.sparql.runtime.incremental.types.Query

class QueryCompatLayer: IncrementalCompatLayer<QueryAST, Query>() {

    override fun convert(source: QueryAST): Query = when (source) {
        is SelectQueryAST -> SelectQueryCompatLayer.convert(source)
    }

}
