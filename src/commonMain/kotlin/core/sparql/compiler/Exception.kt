package core.sparql.compiler

open class CompilerError(message: String, val stacktrace: String): RuntimeException(message)

class SyntaxError(message: String, stacktrace: String): CompilerError(message, stacktrace)

class StructuralError(message: String, stacktrace: String): CompilerError(message, stacktrace)
