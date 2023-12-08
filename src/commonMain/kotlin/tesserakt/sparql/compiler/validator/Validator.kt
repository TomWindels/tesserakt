package tesserakt.sparql.compiler.validator

import tesserakt.sparql.compiler.types.QueryAST

abstract class Validator<AST: QueryAST> {

    abstract fun validate(ast: AST): Boolean

}
