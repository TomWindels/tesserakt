package sparql.tests

import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import sparql.types.tests

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = "${base_uri}Person".asNamedTerm()
    val age = "${base_uri}age".asNamedTerm()
    val knows = "${base_uri}knows".asNamedTerm()
    val based_near = "${base_uri}based_near".asNamedTerm()

}

fun builtinTests() = tests {
    val small = buildStore {
        val subj = local("s")
        val obj = local("o")
        val intermediate = local("i")
        val path1 = "http://example.org/path1".asNamedTerm()
        val path2 = "http://example.org/path2".asNamedTerm()
        subj has path1 being obj
        subj has path2 being obj
        subj has path1 being intermediate
        intermediate has path1 being obj
    }

    using(small) test """
        SELECT * {
            ?s (<http://example.org/path1>|<http://example.org/path2>) ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s (<http://example.org/path1>/!<http://example.org/path2>) ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s !<http://example.org/path3> ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s (<http://example.org/path1>|<http://example.org/path2>)* ?o
        }
    """

    val filtered = buildStore {
        val example = prefix("", "http://example/")
        example("alice") has type being FOAF.Person
        example("alice") has FOAF("name") being example("name")
        example("name") has example("firstName") being "Alice".asLiteralTerm()
        example("name") has example("lastName") being "LastName".asLiteralTerm()
        example("bob") has type being FOAF.Person
    }

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER NOT EXISTS { ?person foaf:name ?name }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER NOT EXISTS { ?a ?b ?c }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER NOT EXISTS { ?a ?b foaf:Person }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER NOT EXISTS { ?a foaf:name ?b }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER NOT EXISTS {
                ?person foaf:name ?name
                FILTER NOT EXISTS {
                    ?name :firstName ?value
                }
            }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER NOT EXISTS {
                ?a foaf:name ?name
                FILTER NOT EXISTS {
                    ?name :firstName ?value
                }
            }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            {
                FILTER NOT EXISTS {
                    ?person foaf:name ?name
                }
            }
            UNION {
                ?person foaf:name ?name
                FILTER NOT EXISTS {
                    ?name :firstName ?firstName
                }
                FILTER NOT EXISTS {
                    ?name :lastName ?lastName
                }
            }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER EXISTS { ?person foaf:name ?name }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER EXISTS { ?a ?b ?c }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER EXISTS { ?a ?b foaf:Person }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER EXISTS { ?a foaf:name ?b }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER EXISTS {
                ?person foaf:name ?name
                FILTER EXISTS {
                    ?name :firstName ?value
                }
            }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            FILTER EXISTS {
                ?a foaf:name ?name
                FILTER EXISTS {
                    ?name :firstName ?value
                }
            }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            {
                FILTER NOT EXISTS {
                    ?person foaf:name ?name
                }
            }
            UNION {
                ?person foaf:name ?name
                FILTER EXISTS {
                    ?name :firstName ?firstName
                }
                FILTER EXISTS {
                    ?name :lastName ?lastName
                }
            }
        }
    """

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            ?person rdf:type  foaf:Person .
            ?person foaf:name ?name
            FILTER EXISTS {
                ?name :firstName ?firstName
            }
            FILTER NOT EXISTS {
                ?name :lastName ?lastName
            }
        }
    """

    val extra = buildStore(path = "http://example.org/") {
        val subj = local("s")
        val obj = local("o")
        val intermediate = local("i")
        val path1 = "http://example.org/path1".asNamedTerm()
        subj has path1 being obj
        subj has path1 being intermediate
        intermediate has path1 being obj
    }

    using(extra) test """
        PREFIX : <http://example.org/>
        SELECT * {
            ?a :path1 ?o .
            ?a :path1 :o .
        }
    """

    val medium = buildStore {
        val person = local("person1")
        person has RDF.type being "http://example.org/person".asNamedTerm()
        person has "http://example.org/age".asNamedTerm() being 23
        person has "http://example.org/notes".asNamedTerm() being list(
            "http://example.org/first-note".asNamedTerm(),
            "http://example.org/second-note".asNamedTerm(),
            "http://example.org/third-note".asNamedTerm(),
            "http://example.org/fourth-note".asNamedTerm(),
            "http://example.org/another-note".asNamedTerm(),
            "http://example.org/last-note".asNamedTerm(),
        )
        person has "http://example.org/notes".asNamedTerm() being list(
            "http://example.org/even-more-notes".asNamedTerm()
        )
        person has "http://example.org/decoy".asNamedTerm() being list(
            "http://example.org/wrong-1".asNamedTerm(),
            "http://example.org/wrong-2".asNamedTerm(),
            "http://example.org/wrong-3".asNamedTerm(),
        )
    }

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?node {
            ?node rdf:rest* ?blank .
            ?blank rdf:rest rdf:nil .
        }
    """

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?person ?note {
            ?person a <http://example.org/person> ; <http://example.org/notes>/rdf:rest*/rdf:first ?note
        }
    """

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT * {
            ?s (<http://example.org/>|!<http://example.org/>)* ?o
        }
    """

    using(medium) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?person ?note ?decoy {
            ?person a <http://example.org/person> .
            {
                ?person <http://example.org/notes>/rdf:rest*/rdf:first ?note
            } UNION {
                ?person <http://example.org/decoy>/rdf:rest*/rdf:first ?decoy
            }
        }
    """

    val chain = buildStore {
        val start = local("start")
        val end = local("end")
        val path = "http://example.org/path".asNamedTerm()
        start has path being end
        start has path being blank {
            path being end
            path being blank {
                path being end
                path being blank {
                    path being end
                }
            }
        }
    }

    using(chain) test """
        SELECT ?s ?e {
            ?s (<http://example.org/path>/<http://example.org/path>)* ?e
        }
    """

    using(chain) test """
        SELECT * {
            # This line is apparently not valid SPARQL; oh well
            # ?s (<http://example.org/path>*)/?p ?e
            ?s <http://example.org/path>* ?b .
            ?b ?p ?e
        }
    """

//    val person = buildStore {
//        val person1 = "http://example.org/person1".asNamedTerm()
//        val person2 = "http://example.org/person2".asNamedTerm()
//        person1 has "http://example.org/givenName".asNamedTerm() being "John".asLiteralTerm()
//        person1 has "http://example.org/surname".asNamedTerm() being "Doe".asLiteralTerm()
//        person2 has "http://example.org/givenName".asNamedTerm() being "A".asLiteralTerm()
//        person2 has "http://example.org/surname".asNamedTerm() being "B".asLiteralTerm()
//    }

//    using(person) test """
//        PREFIX : <http://example.org/>
//        SELECT ?name {
//            ?person :givenName ?gName ; :surname ?sName .
//            BIND(CONCAT(?gName, " ", ?sName) AS ?name)
//            FILTER(STRLEN(?name) > 3)
//        }
//    """

    val fullyConnected = buildStore {
        val a = "http://example.org/a".asNamedTerm()
        val b = "http://example.org/b".asNamedTerm()
        val c = "http://example.org/c".asNamedTerm()
        val p = "http://example.org/p".asNamedTerm()
        a has p being b
        a has p being c
        b has p being a
        b has p being c
        c has p being a
        c has p being b
    }

    using(fullyConnected) test """
        SELECT * WHERE {
            <http://example.org/a> <http://example.org/p>* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a <http://example.org/p>* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a <http://example.org/p>* ?b
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a (<http://example.org/p>/<http://example.org/p>/<http://example.org/p>)* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a ((<http://example.org/p>/<http://example.org/p>)*/<http://example.org/p>)* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            <http://example.org/a> (<http://example.org/p>/<http://example.org/p>/<http://example.org/p>)* ?b
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a (<http://example.org/p>/<http://example.org/p>/<http://example.org/p>)* ?b
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            <http://example.org/c> (<http://example.org/p>/<http://example.org/p>/<http://example.org/p>)* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            <http://example.org/a> <http://example.org/p>+ <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a <http://example.org/p>+ <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a <http://example.org/p>+ ?b
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a (<http://example.org/p>/<http://example.org/p>/<http://example.org/p>)+ <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a (<http://example.org/p>/<http://example.org/p>/<http://example.org/p>)+ ?b
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a ((<http://example.org/p>/<http://example.org/p>)+/<http://example.org/p>)* <http://example.org/b>
        }
    """

    using(fullyConnected) test """
        SELECT * WHERE {
            ?a ((<http://example.org/p>/<http://example.org/p>)*/<http://example.org/p>)+ <http://example.org/b>
        }
    """

    val unions = buildStore("http://www.example.org/") {
        val a = local("a")
        val b = local("b")
        val c = local("c")
        val d = local("d")
        val e = local("e")

        val p1 = local("p1")
        val p2 = local("p2")
        val p3 = local("p3")
        val p4 = local("p4")

        a has p1 being b
        b has p4 being c
        a has p2 being d
        d has p3 being c
        a has p1 being e
    }

    using(unions) test """
        PREFIX : <http://www.example.org/>
        SELECT ?s WHERE {
            {
                :a :p1 ?b .
            } UNION {
                :a :p2 ?b .
            }
            {
                ?b :p3 ?s .
            } UNION {
                ?b :p4 ?s .
            }
        }
    """

    using(unions) test """
        PREFIX : <http://www.example.org/>
        SELECT ?s WHERE {
            :a (:p1|:p2)/(:p3|:p4) ?s
        }
    """

    val literals = buildStore {
        val a = "http://www.example.org/a".asNamedTerm()
        val b = "http://www.example.org/b".asNamedTerm()
        val c = "http://www.example.org/c".asNamedTerm()
        val d = "http://www.example.org/d".asNamedTerm()
        val p = "http://www.example.org/p".asNamedTerm()

        a has p being 11
        a has p being b
        b has p being 12
        b has p being c
        c has p being 13
        c has p being d
        d has p being 14
    }

    using (literals) test """
        PREFIX : <http://www.example.org/>
        SELECT ?v WHERE {
            ?a :p* ?v
        }
    """

    using (literals) test """
        PREFIX : <http://www.example.org/>
        SELECT ?v WHERE {
            ?a :p+ ?v
        }
    """

    val person1 = buildStore {
        val person = local("person1")
        person has RDF.type being FOAF.Person
        person has FOAF.age being 23
        person has FOAF.knows being multiple(
            local("person2"), local("person3"), local("person4")
        )
        person has FOAF.based_near being blank {
            "https://www.example.org/street".asNamedTerm() being "unknown".asLiteralTerm()
            "https://www.example.org/number".asNamedTerm() being (-1).asLiteralTerm()
        }
        person has "notes".asNamedTerm() being list(
            "first-note".asNamedTerm(), "second-note".asNamedTerm()
        )
    }

    using(person1) test "SELECT * WHERE { ?s ?p ?o }"

    using(person1) test """
        PREFIX ex: <https://www.example.org/>
        SELECT * WHERE {
            ?person <${FOAF.based_near}>/ex:number ?number ;
                    <${FOAF.based_near}>/ex:street ?street
        }
    """

    using(person1) test "SELECT ?friend WHERE { ?person <${FOAF.knows}> ?friend ; a <${FOAF.Person}> }"

    using(person1) test "PREFIX ex: <https://www.example.org/> SELECT ?data { ?s a | ex:age|ex:friend ?data }"

    using(person1) test "PREFIX ex: <https://www.example.org/> SELECT ?street { ?s (a | ex:address)/ex:street ?street }"

    using(person1) test "PREFIX ex: <https://www.example.org/> SELECT ?s ?o { ?s (ex:|!ex:) ?o }"

    using(person1) test "PREFIX ex: <https://www.example.org/> SELECT ?s ?o { ?s !(ex:friend|ex:notes|ex:address) ?o }"

    val person2 = buildStore {
        val person = local("person1")
        person has RDF.type being "person".asNamedTerm()
        person has "https://www.example.org/age".asNamedTerm() being 23
        person has "https://www.example.org/notes".asNamedTerm() being list(
            "first-note".asNamedTerm(),
            "second-note".asNamedTerm(),
            "third-note".asNamedTerm(),
            "fourth-note".asNamedTerm(),
            "another-note".asNamedTerm(),
            "last-note".asNamedTerm(),
        )
        person has "https://www.example.org/notes".asNamedTerm() being list(
            "even-more-notes".asNamedTerm()
        )
        person has "https://www.example.org/decoy".asNamedTerm() being list(
            "wrong-1".asNamedTerm(),
            "wrong-2".asNamedTerm(),
            "wrong-3".asNamedTerm(),
        )
    }

    using(person2) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?node {
            ?node rdf:rest* ?blank .
            ?blank rdf:rest rdf:nil .
        }
    """

    using(person2) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT ?node {
            ?node rdf:rest+ ?blank .
            ?blank rdf:rest rdf:nil .
        }
    """

    using(person2) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX ex: <https://www.example.org/>
        SELECT ?person ?note {
            ?person a ex:person ; ex:notes/rdf:rest*/rdf:first ?note
        }
    """

    using(person2) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX ex: <https://www.example.org/>
        SELECT ?person ?note {
            ?person a ex:person ; ex:notes/rdf:rest+/rdf:first ?note
        }
    """

    using(person2) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT * {
            ?s (rdf:|!rdf:)* ?o
        }
    """

    using(person2) test """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT * {
            ?s (rdf:|!rdf:)+ ?o
        }
    """

    val addresses = buildStore {
        "person1".asNamedTerm() has "https://www.example.org/domicile".asNamedTerm() being blank {
            "https://www.example.org/address".asNamedTerm() being blank {
                "https://www.example.org/street".asNamedTerm() being "Person St.".asLiteralTerm()
                "https://www.example.org/city".asNamedTerm() being blank {
                    "https://www.example.org/inhabitants".asNamedTerm() being 5000
                }
            }
        }
        "person2".asNamedTerm() has "https://www.example.org/domicile".asNamedTerm() being "house2".asNamedTerm()
        "house2".asNamedTerm() has "https://www.example.org/address".asNamedTerm() being "address2".asNamedTerm()
        "address2".asNamedTerm() has "https://www.example.org/street".asNamedTerm() being "Person II St.".asLiteralTerm()
        "address2".asNamedTerm() has "https://www.example.org/city".asNamedTerm() being blank {
            "https://www.example.org/inhabitants".asNamedTerm() being 7500
        }
        "incomplete".asNamedTerm() has "https://www.example.org/domicile".asNamedTerm() being blank {
            "https://www.example.org/address".asNamedTerm() being blank {
                "https://www.example.org/street".asNamedTerm() being "unknown".asNamedTerm()
                "https://www.example.org/city".asNamedTerm() being "unknown".asNamedTerm()
            }
        }
    }

    using(addresses) test """
        PREFIX ex: <https://www.example.org/>
        SELECT * {
            ?person ex:domicile [
                ex:address [
                    ex:street ?street ;
                    ex:city [
                        ex:inhabitants ?count
                    ]
                ]
            ] .
        }
    """

//    using(addresses) test """
//        PREFIX ex: <https://www.example.org/>
//        SELECT * {
//            ?person ex:domicile/ex:address ?place .
//            ?place ex:street ?street .
//            OPTIONAL {
//                ?place ex:city/ex:inhabitants ?count .
//            }
//        }
//    """

//    using(addresses) test """
//        PREFIX ex: <https://www.example.org/>
//        SELECT * {
//            ?person ex:domicile/ex:address ?place .
//            ?place ex:street ?street .
//            {
//                ?place ex:city ?city .
//                 OPTIONAL {
//                    ?city ex:inhabitants: ?count .
//                 }
//            } UNION {
//                ?place ex:city ex:unknown .
//            }
//        }
//    """

    using(addresses) test """
        PREFIX ex: <https://www.example.org/>
        SELECT * {
            ?a (ex:domicile/ex:address)|(ex:city/ex:inhabitants) ?b .
        }
    """

//    using(addresses) test """
//        PREFIX ex: <https://www.example.org/>
//        SELECT * {
//            ?person ex:domicile/ex:address ?place .
//            ?place ex:street ?street .
//            {
//                SELECT * { ?s ?p ?o }
//            }
//        }
//    """
//
//    val aggregation = """
//        @prefix : <http://books.example/> .
//
//        :org1 :affiliates :auth1, :auth2 .
//        :auth1 :writesBook :book1, :book2 .
//        :book1 :price 9 .
//        :book2 :price 5 .
//        :auth2 :writesBook :book3 .
//        :book3 :price 7 .
//        :org2 :affiliates :auth3 .
//        :auth3 :writesBook :book4 .
//        :book4 :price 7 .
//    """.parseTurtleString()
//
//    using(aggregation) test """
//        PREFIX : <http://books.example/>
//        SELECT (SUM(?lprice) AS ?totalPrice)
//        WHERE {
//          ?org :affiliates ?auth .
//          ?auth :writesBook ?book .
//          ?book :price ?lprice .
//        }
//        GROUP BY ?org
//        HAVING (SUM(?lprice) > 10)
//    """

    val aux1 = buildStore("http://example.org/") {
        val s0 = local("s0")
        val s1 = local("s1")
        val p1 = local("p1")
        val p2 = local("p2")
        val o = local("o")
        val x = local("x")

        s0 has p2 being x
        x has p1 being o
        s1 has p1 being o
    }

    using(aux1) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?a :p1 ?b .
            ?d :p2 ?c .
            ?c :p1|:p2 :o
        }
    """

    val aux2 = buildStore("http://example.org/") {
        val s0 = local("s0")
        val s1 = local("s1")
        val p1 = local("p1")
        val p2 = local("p2")
        val o = local("o")
        val x = local("x")

        s0 has p2 being x
        s1 has p2 being o
        x has p1 being o
    }

    using(aux2) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?a :p1 ?b .
            ?d :p2 ?c .
            ?c :p1|:p2 :o
        }
    """

}
