package tesserakt.sparql.compiler.validator

import tesserakt.sparql.compiler.types.QueryAST

object Validation {

    private val validators = listOf(
        SelectQueryOutputValidator(),
        PatternPredicateConstrainedValidator(),
    )

    fun validate(ast: QueryAST) = validators.forEach { validator ->
        if (!validator.validate(ast)) {
            // TODO: improve exception
            throw IllegalStateException("Validator `${validator::class.simpleName}` failed!")
        }
    }

}