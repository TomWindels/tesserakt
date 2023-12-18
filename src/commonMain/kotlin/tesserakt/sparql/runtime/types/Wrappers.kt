package tesserakt.sparql.runtime.types

import kotlin.jvm.JvmInline

@JvmInline
value class PatternsASTr(private val items: List<PatternASTr>): List<PatternASTr> by items, ASTr
