package tesserakt.sparql.compiler.types

import kotlin.jvm.JvmInline

@JvmInline
value class UnionAST(val segments: List<SegmentAST>): List<SegmentAST> by segments, AST

@JvmInline
value class OptionalAST(val segment: SegmentAST): AST

sealed interface SegmentAST: AST {

    @JvmInline
    value class Statements(val statements: QueryAST.QueryBodyAST): SegmentAST, AST by statements

    @JvmInline
    value class SelectQuery(val query: SelectQueryAST): SegmentAST, AST by query

}
