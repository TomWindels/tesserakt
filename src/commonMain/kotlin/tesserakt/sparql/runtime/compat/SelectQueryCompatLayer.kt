package tesserakt.sparql.runtime.compat

import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.sparql.runtime.types.SelectQueryASTr

class SelectQueryCompatLayer: CompatLayer<SelectQueryAST, SelectQueryASTr>() {

    override fun convert(source: SelectQueryAST): SelectQueryASTr {
        return SelectQueryASTr(
            output = source.output.names,
            body = QueryBodyCompatLayer().convert(source.body)
        )
    }

}
