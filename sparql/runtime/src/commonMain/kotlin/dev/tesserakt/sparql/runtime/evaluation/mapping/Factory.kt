package dev.tesserakt.sparql.runtime.evaluation.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import kotlin.jvm.JvmName


@JvmName("mappingOfValues")
fun mappingOf(context: QueryContext, vararg pairs: Pair<String, Quad.Element>) =
    if (pairs.isEmpty()) Mapping.EMPTY else Mapping(context, pairs.asIterable())

@JvmName("mappingOfIdentifiers")
fun mappingOf(vararg pairs: Pair<BindingIdentifier, TermIdentifier>) =
    if (pairs.isEmpty()) Mapping.EMPTY else Mapping(pairs.asIterable())

fun Mapping.hashable() = if (count == 0) HashableMapping.EMPTY else HashableMapping(this)
