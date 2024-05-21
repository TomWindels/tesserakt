package dev.tesserakt.sparql.compiler.types

import kotlin.jvm.JvmInline

@JvmInline
value class PatternsAST(private val items: List<PatternAST>): List<PatternAST> by items, AST
