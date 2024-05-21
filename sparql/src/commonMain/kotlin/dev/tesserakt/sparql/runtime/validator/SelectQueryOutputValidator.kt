package dev.tesserakt.sparql.runtime.validator

import dev.tesserakt.sparql.runtime.types.PatternASTr
import dev.tesserakt.sparql.runtime.types.SelectQueryASTr
import dev.tesserakt.sparql.runtime.getAllNamedBindings

object SelectQueryOutputValidator: Validator<SelectQueryASTr>(SelectQueryASTr::class) {

    override fun _validate(ast: SelectQueryASTr): Boolean {
        val bindings = ast.body.getAllNamedBindings()
        return ast.output.all { PatternASTr.RegularBinding(it) in bindings }
    }

}
