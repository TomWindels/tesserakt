package dev.tesserakt.sparql.runtime.evaluation.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import kotlin.jvm.JvmName


fun mappingOf(context: QueryContext, vararg pairs: Pair<String, Quad.Term>) =
    context.create(pairs.asIterable())

@JvmName("mappingOfNullable")
fun mappingOf(context: QueryContext, vararg pairs: Pair<String?, Quad.Term>) =
    @Suppress("UNCHECKED_CAST")
    context.create(pairs.filter { it.first != null } as List<Pair<String, Quad.Term>>)
