package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping

/**
 * A special [Stream] marker type, hinting that it yields [BitsetMapping] instances with
 *  identical [BitsetMapping.bindings] set for every result
 */
interface FixedShapeMappingStream: Stream<Mapping>
