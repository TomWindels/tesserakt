
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.debug.ASTWriter
import dev.tesserakt.sparql.types.runtime.query.Query
import dev.tesserakt.util.console.StylisedWriter

object VerboseCompiler: Compiler() {

    override fun compile(raw: String): Query<*, *> {
        // compiling the input query
        val ast = raw.toAST()
        // outputting the resulting AST
        println(ASTWriter().write(ast))
        // converting it to a subset supported by the runtime
        println("Generated the following runtime AST:")
        println(StylisedWriter.write(ast))
        return ast.createState()
    }

}
