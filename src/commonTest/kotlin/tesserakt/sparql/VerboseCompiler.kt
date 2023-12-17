package tesserakt.sparql

import tesserakt.sparql.runtime.compat.QueryCompatLayer
import tesserakt.sparql.runtime.query.Query
import tesserakt.sparql.runtime.validator.SelectQueryOutputValidator
import tesserakt.sparql.runtime.validator.Validator.Companion.validate
import tesserakt.util.console.StylisedASTrWriter

object VerboseCompiler: Compiler() {

    private val validators = listOf(
        SelectQueryOutputValidator,
    )

    override fun compile(raw: String): Query<*, *> {
        // compiling the input query
        val ast = raw.toAST()
        // converting it to a subset supported by the runtime
        val compat = QueryCompatLayer().convert(ast)
        // validating if the resulting
        validators.validate(compat)
        println("Generated the following runtime AST:")
        println(StylisedASTrWriter.write(compat))
        return compat.toQuery()
    }

}
