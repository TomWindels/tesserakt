package dev.tesserakt.sparql.runtime.common.util

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.Mapping

/**
 * Extracts the term value associated with `this` subject based on the provided `mapping`, or `null` if a term
 *  should've been bounded but was not found (i.e. unconstrained)
 */
internal fun Pattern.Subject.getTermOrNull(mapping: Mapping): Quad.Term? =
    when (this) {
        is Pattern.Binding -> mapping[name]
        is Pattern.Exact -> term
    }

internal fun Pattern.Object.getTermOrNull(mapping: Mapping): Quad.Term? =
    when (this) {
        is Pattern.Binding -> mapping[name]
        is Pattern.Exact -> term
    }
