package dev.tesserakt.sparql.compiler.ast

import kotlin.jvm.JvmInline

@JvmInline
value class PatternsAST(private val items: List<PatternAST>): List<PatternAST> by items, ASTNode
