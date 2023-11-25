package core.rdf

import core.sparql.compiler.Analyser
import core.sparql.compiler.CompilerError
import core.sparql.compiler.lexer.StringLexer
import util.printerrln

class SPARQL {

    companion object {

        fun parse(raw: String) {
            val lexer = StringLexer(raw)
            try {
                val analyser = Analyser(lexer)
            } catch (e: CompilerError) {
                printerrln("${e::class.simpleName}: ${e.problem}\n${lexer.stacktrace(e.description)}")
            }
        }

    }

}
