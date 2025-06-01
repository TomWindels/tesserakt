package dev.tesserakt.sparql.runtime.evaluation.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import kotlin.jvm.JvmName


fun mappingOf(context: QueryContext, vararg pairs: Pair<String, Quad.Element>) =
    context.create(pairs.asIterable())

@JvmName("mappingFromIdentifiers")
fun mappingOf(context: QueryContext, vararg pairs: Pair<BindingIdentifier, TermIdentifier>) =
    context.createFromIdentifiers(pairs.asIterable())

@JvmName("mappingOfNullable")
fun mappingOf(context: QueryContext, vararg pairs: Pair<String?, Quad.Element>) =
    @Suppress("UNCHECKED_CAST")
    context.create(pairs.filter { it.first != null } as List<Pair<String, Quad.Element>>)
