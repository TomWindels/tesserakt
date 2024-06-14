package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.sparql.runtime.node.IncrementalNode

sealed class Query: IncrementalNode {

    abstract val body: QueryBody

    data class QueryBody(
        /** The full pattern block that is required **/
        val patterns: Patterns,
        /** All binding statements found inside this pattern block (similar to filters) **/
        val bindingStatements: List<BindingStatement>,
        /** All filters applied to this pattern block (optional / union filters NOT included) **/
        val filters: List<Filter>,
        /** All requested unions, not yet flattened to allow for easier optimisation **/
        val unions: List<Union>,
        /** Collection of pattern blocks that are optional **/
        val optional: List<Optional>
    ): IncrementalNode

}
