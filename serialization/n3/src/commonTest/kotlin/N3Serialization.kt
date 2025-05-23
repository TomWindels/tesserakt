
import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.dsl.N3
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.n3.serialization.N3Serializer
import kotlin.test.Test

@OptIn(ExperimentalN3Api::class)
class N3Serialization {

    @Test
    fun serialize0() {
        // based on https://w3c.github.io/N3/spec/#example-21
        val store = N3 {
            val s = statements { local("cervantes") has local("wrote") being local("moby_dick") }
            s has local("opinion") being local("lie")
            s has local("retrievedFrom") being local("http://lies_r_us.com")
            s has local("retrievedAt") being "2020-07-12T09:01:33".asLiteralTerm(type = XSD.dateTime)
        }
        println(N3Serializer.serialize(store))
    }

    @Test
    fun serialize1() {
        // based on https://w3c.github.io/N3/spec/#example-22
        val store = N3 {
            "LoisLane".asNamedTerm() has "believes".asNamedTerm() being statements {
                local("Superman") has local("can") being local("fly")
            }
        }
        println(N3Serializer.serialize(store))
    }

    @Test
    fun serialize2() {
        val store = N3 {
            val change = local("change1")
            change has RDF.type being local("Addition")
            change has local("order") being 0
            change has local("data") being statements {
                repeat(10) {
                    local("s$it") has local("p$it") being local("o$it")
                }
            }
        }
        println(N3Serializer.serialize(store))
    }

    @Test
    fun serialize3() {
        val store = N3 {
            val changes = local("changeset")
            changes has RDF.type being local("ChangeSet")
            changes has local("changes") being statements {
                repeat(5) { index ->
                    val subject = statements {
                        repeat(2) { i ->
                            val data = index + i
                            local("s$data") has local("p$data") being local("o$data")
                        }
                    }
                    subject has RDF.type being if (index % 2 == 0) local("Addition") else local("Deletion")
                    subject has local("order") being index
                }
            }
        }
        println(N3Serializer.serialize(store))
    }

}
