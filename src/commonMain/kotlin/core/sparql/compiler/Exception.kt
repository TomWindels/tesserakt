package core.sparql.compiler

open class CompilerError(val problem: String, val description: String): RuntimeException()

class SyntaxError(problem: String, description: String): CompilerError(problem, description)

class StructuralError(problem: String, description: String): CompilerError(problem, description)
