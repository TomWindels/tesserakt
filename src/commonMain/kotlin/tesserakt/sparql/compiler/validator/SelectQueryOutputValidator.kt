package tesserakt.sparql.compiler.validator

import tesserakt.sparql.compiler.extractAllBindings
import tesserakt.sparql.compiler.types.SelectQueryAST

class SelectQueryOutputValidator: Validator<SelectQueryAST>(SelectQueryAST::class) {

    override fun _validate(ast: SelectQueryAST): Boolean {
        val availablePatterns = ast.body.extractAllBindings().map { it.name }.toSet()
        return availablePatterns.containsAll(ast.output.names)
    }

}
