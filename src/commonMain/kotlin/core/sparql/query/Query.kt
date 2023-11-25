package core.sparql.query

import core.sparql.compiler.Pattern

// can later be overridden for construct blocks, select blocks, ...
data class Query(
    // later: preproc (COUNT Y, MATH, ...)
    // SELECT ?exports WHERE
    val exports: List<Pattern.Binding>,
    // { ... } consisting out of PatternBlock & potential subqueries
    val block: PatternBlock,
    // TODO: as optimisation, parameters in sub exports not mentioned in `block` and own `exports` can be removed
    val sub: List<Query>
    // later: postproc (LIMIT Y, ORDER BY Z)
) {

}

//data class Select(
//    val exports: List<>
//): QueryBlock() {
//
//}

//class Construct: QueryBlock() {
//
//}
