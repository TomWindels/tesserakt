package dev.tesserakt.mapping

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.runtime.evaluation.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import kotlin.math.roundToInt
import kotlin.random.Random


const val SIZE = 3_500
const val VARIANCE = 50
val BINDINGS = listOf(
    "person" to List(VARIANCE) { "http://example/person_${it}".asNamedTerm() },
    "job" to List(VARIANCE) { "http://example/job_${it}".asNamedTerm() },
    "name" to List(VARIANCE) { "http://example/name_${it}".asNamedTerm() },
    "age" to List(VARIANCE) { it.asLiteralTerm() },
)

typealias MapMapping = Map<String, Quad.Term>

private fun createMapping(id: Int) : Map<String, Quad.Term> {
    val rng = Random(id)
    return BINDINGS.associate { it.first to it.second.random(rng) }.filter { rng.nextBoolean() }
}

fun generateMappingSet(seed: Int): List<Map<String, Quad.Term>> {
    val random = Random(seed)
    return List(((random.nextFloat() + 0.5f) * SIZE).roundToInt()) { createMapping(random.nextInt()) }
}

fun Map<String, Quad.Term>.toMapping() = Mapping(GlobalQueryContext, this)
