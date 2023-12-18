package tesserakt.sparql.runtime.types

import kotlin.jvm.JvmInline

@JvmInline
value class UnionASTr(private val items: List<Segment>): List<UnionASTr.Segment> by items, ASTr {

    sealed interface Segment

    @JvmInline
    value class StatementsSegment(val statements: QueryASTr.QueryBodyASTr): Segment

    @JvmInline
    value class SelectQuerySegment(val query: SelectQueryASTr): Segment

}
