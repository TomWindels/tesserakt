package dev.tesserakt.sparql.runtime.common.types

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad

object Terms {

    val TRUE = Quad.Literal(true, XSD.boolean)
    val FALSE = Quad.Literal(false, XSD.boolean)

}
