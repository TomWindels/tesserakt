package tesserakt.sparql.compiler

class CompilerError(
    message: String,
    val type: Type,
    val stacktrace: String
): RuntimeException("$type: $message") {

    enum class Type {
        SyntaxError,
        StructuralError,
    }

}
