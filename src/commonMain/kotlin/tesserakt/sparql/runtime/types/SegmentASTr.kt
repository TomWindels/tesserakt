package tesserakt.sparql.runtime.types

import kotlin.jvm.JvmInline

@JvmInline
value class UnionASTr(val segments: List<SegmentASTr>): List<SegmentASTr> by segments, ASTr

@JvmInline
value class OptionalASTr(val segment: SegmentASTr): ASTr

sealed interface SegmentASTr {

    @JvmInline
    value class Statements(val statements: QueryASTr.QueryBodyASTr): SegmentASTr, ASTr by statements

    @JvmInline
    value class SelectQuery(val query: SelectQueryASTr): SegmentASTr, ASTr by query

}
