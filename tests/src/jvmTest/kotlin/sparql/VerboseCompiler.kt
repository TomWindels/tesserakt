package sparql

import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.runtime.compat.QueryCompatLayer
import dev.tesserakt.sparql.runtime.query.Query
import dev.tesserakt.sparql.runtime.validator.SelectQueryOutputValidator
import dev.tesserakt.sparql.runtime.validator.Validator.Companion.validate
import dev.tesserakt.util.console.StylisedASTrWriter

object VerboseCompiler: Compiler() {

    private val validators = listOf(
        SelectQueryOutputValidator,
    )

    override fun compile(raw: String): Query<*, *> {
        // compiling the input query
        val ast = raw.toAST()
        // outputting the resulting AST
        println(ast)
        // converting it to a subset supported by the runtime
        val compat = QueryCompatLayer().convert(ast)
        // validating if the resulting
        validators.validate(compat)
        println("Generated the following runtime AST:")
        println(StylisedASTrWriter.write(compat))
        return compat.toQuery()
    }

}
