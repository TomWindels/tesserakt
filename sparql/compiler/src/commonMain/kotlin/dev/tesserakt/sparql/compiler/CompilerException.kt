package dev.tesserakt.sparql.compiler

import dev.tesserakt.sparql.SparqlException

class CompilerException(
    message: String,
    val type: Type,
    val stacktrace: String
): SparqlException("$type: $message\n$stacktrace") {

    enum class Type {
        SyntaxError,
        StructuralError,
    }

}
