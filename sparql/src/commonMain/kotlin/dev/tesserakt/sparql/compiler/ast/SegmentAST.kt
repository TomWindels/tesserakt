package dev.tesserakt.sparql.compiler.ast

import kotlin.jvm.JvmInline

@JvmInline
value class UnionAST(val segments: List<SegmentAST>): List<SegmentAST> by segments, ASTNode

@JvmInline
value class OptionalAST(val segment: SegmentAST): ASTNode

sealed interface SegmentAST: ASTNode {

    @JvmInline
    value class Statements(val statements: QueryAST.QueryBodyAST): SegmentAST, ASTNode by statements

    @JvmInline
    value class SelectQuery(val query: SelectQueryAST): SegmentAST, ASTNode by query

}
