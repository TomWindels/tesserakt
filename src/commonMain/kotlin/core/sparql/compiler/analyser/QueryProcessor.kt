package core.sparql.compiler.analyser

import core.sparql.compiler.lexer.Lexer
import core.sparql.compiler.types.QueryAST
import core.sparql.compiler.types.Token

class QueryProcessor: Analyser<QueryAST>() {

    private lateinit var result: QueryAST

    init {
        // starting with empty prefixes that gets shared to the sub-analysers
        prefixes = mutableMapOf()
    }

    /**
     * Processes a query from the provided `lexer`
     */
    // simply exposing the underlying analyser implementation
    fun process(lexer: Lexer) = configureAndUse(lexer)

    override fun _process(): QueryAST {
        processQuery()
        return result
    }

    private fun processQuery() = when (token) {
        Token.Syntax.Prefix -> processPrefix()
        Token.Syntax.Select -> use(SelectQueryProcessor()).let { result = it }
        Token.Syntax.Construct -> bail("Construct queries are currently not supported.")
        else -> expectedToken(
            Token.Syntax.Prefix,
            Token.Syntax.Select,
            Token.Syntax.Construct
        )
    }

    private fun processPrefix() {
        // current token should be `PREFIX`, so consuming the next two tokens for the actual prefix
        consumeOrBail()
        val name = (token as Token.Term).value
        consumeOrBail()
        val value = (token as Token.Term).value
        // adding it to the table of prefixes, without the surrounding `<`, `>`
        prefixes[name.substring(0, name.length - 1)] = value.substring(1, value.length - 1)
        // advancing the rest of the query
        consumeOrBail()
        // will either go back processing another prefix, or
        return processQuery()
    }

}