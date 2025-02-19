package dev.tesserakt.sparql.benchmark.replay

private val commentRegex = Regex("#.+$", RegexOption.MULTILINE)
private val whitespaceRegex = Regex("\\s+")

internal fun String.toCleanedUpQuery(): String = this
    .replace(commentRegex, " ")
    .replace(whitespaceRegex, " ")
    .trim()
