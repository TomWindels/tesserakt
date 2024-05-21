package dev.tesserakt.sparql.runtime.types

data class SelectQueryASTr(
    // the output can later be further implemented to support aggregates in its implementation
    val output: Set<String>,
    override val body: QueryBodyASTr
): QueryASTr()
