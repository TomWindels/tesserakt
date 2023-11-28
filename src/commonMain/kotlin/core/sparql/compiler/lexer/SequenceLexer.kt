package core.sparql.compiler.lexer

import core.sparql.compiler.types.Token

/**
 * Lexer capable of processing larger inputs (e.g. from multiple files) not requiring them to be read
 *  in their entirety at once, backed by a Kotlin Sequence
 */
class SequenceLexer: Lexer() {

    override fun hasNext(): Boolean {
        TODO("SequenceLexer is currently not yet supported!")
    }

    override fun next(): Token {
        TODO("SequenceLexer is currently not yet supported!")
    }

    override fun stacktrace(message: String): String {
        TODO("SequenceLexer is currently not yet supported!")
    }

    override fun position(): Int {
        TODO("SequenceLexer is currently not yet supported!")
    }

}
