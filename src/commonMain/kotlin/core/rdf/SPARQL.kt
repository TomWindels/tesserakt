package core.rdf

import core.sparql.compiler.CompilerError
import core.sparql.compiler.analyser.SelectQueryProcessor
import core.sparql.compiler.lexer.StringLexer
import util.printerrln

class SPARQL {

    companion object {

        fun parse(raw: String) {
            val lexer = StringLexer(raw)
            try {
                val result = SelectQueryProcessor().process(lexer)
                println(result)
            } catch (e: CompilerError) {
                printerrln("${e::class.simpleName}: ${e.problem}\n${lexer.stacktrace(e.description)}")
                throw e
            }
        }

    }

}
