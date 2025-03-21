package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Lexer
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.QueryStructure

class QueryProcessor: Analyser<QueryStructure>() {

    private lateinit var result: QueryStructure

    init {
        // starting with empty prefixes that gets shared to the sub-analysers
        prefixes = mutableMapOf()
    }

    /**
     * Processes a query from the provided `lexer`
     */
    // simply exposing the underlying analyser implementation
    fun process(lexer: Lexer) = configureAndUse(lexer)

    override fun _process(): QueryStructure {
        processQuery()
        expectToken(Token.EOF)
        return result
    }

    private fun processQuery() = when (token) {
        Token.Keyword.Prefix -> processPrefix()
        Token.Keyword.Select -> use(SelectQueryProcessor()).let { result = it }
        Token.Keyword.Construct -> bail("Construct queries are currently not supported.")
        else -> expectedToken(
            Token.Keyword.Prefix,
            Token.Keyword.Select,
            Token.Keyword.Construct
        )
    }

    private fun processPrefix() {
        // current token should be `PREFIX`, so consuming the next two tokens for the actual prefix
        consume()
        val namespace = (token as Token.PrefixedTerm).namespace
        consume()
        val value = (token as Token.Term).value
        // adding it to the table of prefixes
        prefixes[namespace] = value
        // advancing the rest of the query
        consume()
        // will either go back processing another prefix, or
        return processQuery()
    }

}
