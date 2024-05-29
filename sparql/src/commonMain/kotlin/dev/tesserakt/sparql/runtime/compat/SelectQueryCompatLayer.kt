package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.compiler.ast.SelectQueryAST
import dev.tesserakt.sparql.runtime.types.SelectQueryASTr

class SelectQueryCompatLayer: CompatLayer<SelectQueryAST, SelectQueryASTr>() {

    override fun convert(source: SelectQueryAST): SelectQueryASTr {
        return SelectQueryASTr(
            output = source.output.names,
            body = QueryBodyCompatLayer().convert(source.body)
        )
    }

}
