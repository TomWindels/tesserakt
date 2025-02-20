package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import kotlinx.datetime.Instant
import kotlin.math.sign

object DateComparator: Comparator<Quad.Literal> {

    override fun compare(a: Quad.Literal, b: Quad.Literal): Int {
        require(a.type == b.type) { "Cannot compare different literal datatypes when comparing $a with $b" }
        require(a.type == XSD.date) { "Literal with type ${a.type} is not a valid date!" }
        return Instant.parse(a.value).minus(Instant.parse(b.value)).inWholeNanoseconds.sign
    }

}
