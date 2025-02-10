
import dev.tesserakt.rdf.dsl.RDF
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import kotlin.test.Test

class TriGSerialization {

    // based on https://www.w3.org/TR/trig/ example 1
    @Test
    fun serialize0() = serialize {
        val ex = prefix("ex", "http://www.example.org/vocabulary#")
        val document = prefix("", "http://www.example.org/exampleDocument#")
        graph(document("G1")) {
            val monica = document("Monica")
            monica has type being ex("Person")
            monica has ex("name") being "Monica Murphy".asLiteralTerm()
            monica has ex("homepage") being "http://www.monicamurphy.org".asNamedTerm()
            monica has ex("email") being "mailto:monica@monicamurphy.org".asNamedTerm()
            monica has ex("hasSkill") being multiple(
                ex("management"),
                ex("programming")
            )
        }
    }

    // smaller; testing inlining behaviour
    @Test
    fun serialize1() = serialize {
        graph("my-graph") {
            local("graph") has type being local("test")
        }
        local("data") has local("graph") being local("my-graph")
    }

    private fun serialize(block: RDF.() -> Unit) {
        println(TriGSerializer.serialize(buildStore(block = block), prefixes = block.extractPrefixes()))
    }

}
