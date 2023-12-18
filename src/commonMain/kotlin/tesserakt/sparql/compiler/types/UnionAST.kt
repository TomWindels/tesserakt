package tesserakt.sparql.compiler.types

import kotlin.jvm.JvmInline

@JvmInline
value class UnionAST(private val items: List<Segment>): List<UnionAST.Segment> by items, AST {

    sealed interface Segment

    @JvmInline
    value class StatementsSegment(val statements: QueryAST.QueryBodyAST): Segment, AST by statements

    @JvmInline
    value class SelectQuerySegment(val query: SelectQueryAST): Segment, AST by query

}
