package dev.tesserakt.sparql.compiler.ast

sealed class QueryAST: ASTNode {

    abstract val body: QueryBodyAST

    class QueryBodyASTBuilder {

        // patterns applied everywhere
        private val _globals = mutableListOf<PatternAST>()
        // collections of sections not required to be being present (`OPTIONAL {}`)
        private val _optionals = mutableListOf<SegmentAST>()
        // collections of multiple segments where one or the other has to be present (`{} UNION {}`)
        private val _unions = mutableListOf<List<SegmentAST>>()

        /** Constructs the `QueryBodyAST` using all combinations of patterns that are checked for **/
        fun build(): QueryBodyAST {
            return QueryBodyAST(
                patterns = PatternsAST(_globals),
                unions = _unions.map { union -> UnionAST(union) },
                optional = _optionals.map { optional -> OptionalAST(optional) }
            )
        }

        /** Appends global patterns to the body **/
        fun addPatterns(patterns: Collection<PatternAST>) {
            _globals.addAll(patterns)
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
        /** All requested unions, not yet flattened to allow for easier optimisation **/
        val unions: List<UnionAST>,
        /** Collection of pattern blocks that are optional **/
        val optional: List<OptionalAST>
    ): ASTNode

}
