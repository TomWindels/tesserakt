package dev.tesserakt.sparql.compiler

class CompilerError(
    message: String,
    val type: Type,
    val stacktrace: String
): RuntimeException("$type: $message\n$stacktrace") {

    enum class Type {
        SyntaxError,
        StructuralError,
    }

}
