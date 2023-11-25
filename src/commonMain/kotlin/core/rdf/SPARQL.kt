package core.rdf

import core.sparql.compiler.Analyser
import core.sparql.compiler.CompilerError
import core.sparql.compiler.Lexer
import util.printerrln

class SPARQL {

    companion object {

        fun parse(raw: String) {
            val lexer = Lexer(raw)
            try {
                val analyser = Analyser(lexer.tokenize().iterator())
            } catch (e: CompilerError) {
                printerrln("${e::class.simpleName}: ${e.problem}\n${lexer.stacktrace(e.description)}")
            }
        }

    }

}
