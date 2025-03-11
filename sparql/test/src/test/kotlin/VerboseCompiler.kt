import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.conversion.QueryCompatLayer
import dev.tesserakt.sparql.types.runtime.query.Query
import dev.tesserakt.util.console.StylisedWriter

object VerboseCompiler: Compiler() {

    override fun compile(raw: String): Query<*, *> {
        // compiling the input query
        val ast = raw.toAST()
        // outputting the resulting AST
        println(ast)
        // converting it to a subset supported by the runtime
        val compat = QueryCompatLayer().convert(ast)
        println("Generated the following runtime AST:")
        println(StylisedWriter.write(compat))
        return compat.toIncrementalQuery()
    }

}
