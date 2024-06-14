package dev.tesserakt.sparql.runtime.common.types

fun Boolean.asLiteralTerm() = if (this) Terms.TRUE else Terms.FALSE
