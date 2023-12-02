package core.sparql.compiler.types

@Suppress("SpellCheckingInspection")
sealed class QueryAST {

    abstract val subqueries: List<QueryAST>
    abstract val body: QueryBodyAST

    class QueryBodyASTBuilder {

        // patterns applied everywhere
        private val _globals = mutableListOf<Pattern>()
        // collections of pattern blocks not required to be being present (`OPTIONAL {}`)
        private val _optionals = mutableListOf<Patterns>()
        // collections of patterns where one or the other has to be present (`{} UNION {}`)
        private val _unions = mutableListOf<List<List<Pattern>>>()

        /** Constructs the `QueryBodyAST` using all combinations of patterns that are checked for **/
        fun build(): QueryBodyAST {
            return QueryBodyAST(
                patterns = _globals,
                unions = _unions,
                optional = _optionals
            )
        }

        /** Appends global patterns to the body **/
        fun addPatterns(patterns: Collection<Pattern>) {
            _globals.addAll(patterns)
        }

        /**
         * Appends a new union to the body. `blocks` represents
         * `{ A } UNION { B } UNION { C } ...` => `listOf(A, B, C, ...)`
         */
        fun addUnion(blocks: List<List<Pattern>>) {
            _unions.add(blocks)
        }

        /** Appends a new optional to the body **/
        fun addOptional(optional: List<Pattern>) {
            _optionals.add(optional)
        }

    }

    data class QueryBodyAST(
        /** The entire pattern block that is required in its entiry **/
        val patterns: Patterns,
        /** All requested unions, not yet flattened to allow for easier optimisation **/
        val unions: List<Union>,
        /** Collection of pattern blocks that are optional **/
        val optional: List<Patterns>
    )

}
