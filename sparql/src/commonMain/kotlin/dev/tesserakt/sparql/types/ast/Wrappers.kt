package dev.tesserakt.sparql.types.ast

import kotlin.jvm.JvmInline

@JvmInline
value class PatternsAST(private val items: List<PatternAST>): List<PatternAST> by items, ASTElement

@JvmInline
value class UnionAST(val segments: List<SegmentAST>): List<SegmentAST> by segments, ASTElement

@JvmInline
value class OptionalAST(val segment: SegmentAST): ASTElement

sealed interface SegmentAST: ASTElement {

    @JvmInline
    value class Statements(val statements: QueryAST.QueryBodyAST): SegmentAST, ASTElement by statements

    @JvmInline
    value class SelectQuery(val query: SelectQueryAST): SegmentAST, ASTElement by query

}
