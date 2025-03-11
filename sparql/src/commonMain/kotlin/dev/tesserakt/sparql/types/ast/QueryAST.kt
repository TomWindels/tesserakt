package dev.tesserakt.sparql.types.ast

import dev.tesserakt.sparql.formatting.ASTWriter

sealed class QueryAST: ASTElement {

    abstract val body: QueryBodyAST

    class QueryBodyASTBuilder {

        // patterns applied everywhere
        private val _globals = mutableListOf<PatternAST>()
        // filters applied to these ^ patterns
        private val _filters = mutableListOf<FilterAST>()
        private val _bindingStatements = mutableListOf<ExpressionAST.BindingStatement>()
        // collections of sections not required to be being present (`OPTIONAL {}`)
        private val _optionals = mutableListOf<SegmentAST>()
        // collections of multiple segments where one or the other has to be present (`{} UNION {}`)
        private val _unions = mutableListOf<List<SegmentAST>>()

        /** Constructs the `QueryBodyAST` using all combinations of patterns that are checked for **/
        fun build(): QueryBodyAST {
            return QueryBodyAST(
                patterns = PatternsAST(_globals),
                filters = _filters,
                bindingStatements = _bindingStatements,
                unions = _unions.map { union -> UnionAST(union) },
                optionals = _optionals.map { optional -> OptionalAST(optional) }
            )
        }

        /** Appends global patterns to the body **/
        fun addPatterns(patterns: Collection<PatternAST>) {
            _globals.addAll(patterns)
        }

        fun addFilter(filter: FilterAST) {
            _filters.add(filter)
        }

        fun addBindStatement(statement: ExpressionAST.BindingStatement) {
            _bindingStatements.add(statement)
        }

        /**
         * Appends a new union to the body. `blocks` represents
         * `{ A } UNION { B } UNION { C } ...` => `listOf(A, B, C, ...)`
         */
        fun addUnion(blocks: List<SegmentAST>) {
            _unions.add(blocks)
        }

        /** Appends a new optional to the body **/
        fun addOptional(optional: SegmentAST) {
            _optionals.add(optional)
        }

    }

    data class QueryBodyAST(
        /** The full pattern block that is required **/
        val patterns: PatternsAST,
        /** All filters applied to this pattern block (optional / union filters NOT included) **/
        val filters: List<FilterAST>,
        /** All binding statements found inside this pattern block (similar to filters) **/
        val bindingStatements: List<ExpressionAST.BindingStatement>,
        /** All requested unions, not yet flattened to allow for easier optimisation **/
        val unions: List<UnionAST>,
        /** Collection of pattern blocks that are optional **/
        val optionals: List<OptionalAST>
    ): ASTElement

    override fun toString(): String {
        return ASTWriter().write(this)
    }

}
