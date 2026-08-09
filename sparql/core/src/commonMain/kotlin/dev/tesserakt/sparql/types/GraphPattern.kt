package dev.tesserakt.sparql.types

data class GraphPattern(
    /**
     * The ordered statements, ordered according to the input query.
     * Contains regular [TriplePattern]s, [Union]s and [Optional]s.
     */
    val statements: List<Statement>,
    /** All binding statements found inside this pattern block (similar to filters) **/
    val bindingStatements: List<BindingStatement>,
    /** All filters applied to this pattern block (optional / union filters NOT included) **/
    val filters: List<Filter>,
): QueryAtom {

    sealed interface Statement

}
