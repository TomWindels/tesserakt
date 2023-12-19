package tesserakt.sparql.runtime.validator

import tesserakt.sparql.runtime.getAllNamedBindings
import tesserakt.sparql.runtime.types.PatternASTr
import tesserakt.sparql.runtime.types.SelectQueryASTr

object SelectQueryOutputValidator: Validator<SelectQueryASTr>(SelectQueryASTr::class) {

    override fun _validate(ast: SelectQueryASTr): Boolean {
        val bindings = ast.body.getAllNamedBindings()
        return ast.output.all { PatternASTr.RegularBinding(it) in bindings }
    }

}
