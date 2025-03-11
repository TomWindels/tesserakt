package dev.tesserakt.sparql.conversion

import dev.tesserakt.sparql.types.ast.QueryAST
import dev.tesserakt.sparql.types.ast.SelectQueryAST
import dev.tesserakt.sparql.types.runtime.element.Query

class QueryCompatLayer: IncrementalCompatLayer<QueryAST, Query>() {

    override fun convert(source: QueryAST): Query = when (source) {
        is SelectQueryAST -> SelectQueryCompatLayer.convert(source)
    }

}