package dev.tesserakt.sparql.ast

import dev.tesserakt.sparql.compiler.extractAllBindings

data class CompiledSelectQuery(
    // the output can later be further implemented to support aggregates in its implementation
    val output: List<Output>?,
    override val body: GraphPattern,
    /** GROUP BY <expr> **/
    val grouping: Expression?,
    /** HAVING (filter) **/
    val groupingFilter: Expression?,
    /** ORDER BY <expr> **/
    val ordering: Expression?
): CompiledQuery() {

    val bindings: Set<String> = output
        ?.mapTo(mutableSetOf()) { it.name } ?: body.extractAllBindings().mapNotNullTo(mutableSetOf()) { (it as? TriplePattern.NamedBinding)?.name }

    sealed class Output {
        abstract val name: String
    }

    data class BindingOutput(
        override val name: String
    ): Output()

    data class ExpressionOutput(
        override val name: String,
        // could be anything out; a number, a term, ...
        val expression: Expression
    ): Output()

}
