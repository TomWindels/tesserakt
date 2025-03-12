
import dev.tesserakt.sparql.Compiler
import dev.tesserakt.sparql.debug.ASTWriter
import dev.tesserakt.util.console.StylisedWriter

object VerboseCompiler: Compiler() {

    override fun compile(query: String): CompiledQuery {
        // compiling the input query
        val compiled = super.compile(query)
        // outputting the resulting AST
        println(ASTWriter().write(compiled.structure))
        // converting it to a subset supported by the runtime
        println("Generated the following runtime AST:")
        println(StylisedWriter.write(compiled.structure))
        return compiled
    }

}
