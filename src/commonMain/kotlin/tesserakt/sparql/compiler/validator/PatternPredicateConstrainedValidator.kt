package tesserakt.sparql.compiler.validator

import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.QueryAST

object PatternPredicateConstrainedValidator: Validator<QueryAST>(QueryAST::class) {

    // TODO: recurse through subquery ASTs
    override fun _validate(ast: QueryAST): Boolean =
        ast.body.patterns.all { it.p.validate() }

    /* helpers */

    private fun Pattern.Predicate.validate(): Boolean = when (this) {
        // nothing but exact pattern elements are allowed for constrained types for the current query
        //  implementation
        is Pattern.Constrained -> allowed.all { item -> item is Pattern.Exact || item is Pattern.Not }
        is Pattern.Chain -> list.all { item -> item.validate() }
        is Pattern.Not -> predicate is Pattern.Constrained || predicate is Pattern.Exact
        is Pattern.ZeroOrMore -> value.validate()
        is Pattern.Binding -> true
        is Pattern.Exact -> true
    }

}
