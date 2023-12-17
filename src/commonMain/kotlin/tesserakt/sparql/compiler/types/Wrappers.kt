package tesserakt.sparql.compiler.types

import kotlin.jvm.JvmInline

@JvmInline
value class PatternsAST(private val items: List<PatternAST>): List<PatternAST> by items, AST

@JvmInline
value class UnionAST(private val items: List<PatternsAST>): List<PatternsAST> by items, AST
