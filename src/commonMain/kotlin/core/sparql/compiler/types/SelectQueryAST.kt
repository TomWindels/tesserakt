package core.sparql.compiler.types

data class SelectQueryAST(
    val bindings: MutableSet<Pattern.Binding> = mutableSetOf(),
    override val body: QueryBodyAST
): QueryAST() {

    class Builder {

        val bindings = mutableSetOf<Pattern.Binding>()
        var grabAllBindings: Boolean = false
        val body = QueryBodyASTBuilder()

        fun build(): SelectQueryAST {
            return SelectQueryAST(
                bindings = bindings,
                body = body.build()
            )
        }

    }

    override val subqueries: List<QueryAST>
        // FIXME when subqueries are supported
        get() = emptyList()

}
