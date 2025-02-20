package dev.tesserakt.sparql.benchmark.replay

private val Whitespace = Regex("\\s+")

internal fun String.toCleanedUpQuery(): String = this
    .lineSequence()
    .map { it.withoutSparqlComments() }
    .joinToString(" ")
    .replace(Whitespace, " ")
    .trim()

private fun String.withoutSparqlComments(): String {
    var inLiteral = false
    var inTerm = false
    forEachIndexed { i, c ->
        if (c == '#' && !inLiteral && !inTerm) {
            return take(i - 1)
        }
        if (c == '"') {
            inLiteral = !inLiteral
        }
        if (c == '<') {
            inTerm = true
        }
        if (c == '>') {
            inTerm = false
        }
    }
    return this
}
