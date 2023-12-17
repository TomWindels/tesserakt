package tesserakt.sparql.runtime.validator

import tesserakt.sparql.runtime.getAllNamedBindings
import tesserakt.sparql.runtime.types.SelectQueryASTr

object SelectQueryOutputValidator: Validator<SelectQueryASTr>(SelectQueryASTr::class) {

    override fun _validate(ast: SelectQueryASTr): Boolean {
        val availablePatterns = ast.body.getAllNamedBindings().map { it.name }.toSet()
        return availablePatterns.containsAll(ast.output)
    }

}
