package dev.tesserakt.sparql.runtime.types

sealed class QueryASTr: ASTr {

    abstract val body: QueryBodyASTr

    data class QueryBodyASTr(
        /** The full pattern block that is required **/
        val patterns: PatternsASTr,
        /** All requested unions, not yet flattened to allow for easier optimisation **/
        val unions: List<UnionASTr>,
        /** Collection of pattern blocks that are optional **/
        val optional: List<OptionalASTr>
    ): ASTr

}
