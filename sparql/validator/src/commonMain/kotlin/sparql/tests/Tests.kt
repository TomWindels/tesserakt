package sparql.tests

import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.NamedTerm
import sparql.types.tests

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = NamedTerm("${base_uri}Person")
    val age = NamedTerm("${base_uri}age")
    val knows = NamedTerm("${base_uri}knows")
    val based_near = NamedTerm("${base_uri}based_near")

}

fun builtinTests() = tests {
    filter = DefaultTestFiltering

    val small = buildStore {
        val subj = local("s")
        val obj = local("o")
        val intermediate = local("i")
        val path1 = NamedTerm("http://example.org/path1")
        val path2 = NamedTerm("http://example.org/path2")
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
            ?s (<http://example.org/path1>|<http://example.org/path2>) ?s
        }
    """

    using(small) test """
        SELECT * {
            ?s ?s ?s
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

    val counts = buildStore {
        val example = prefix("", "http://example/")
        repeat(10) { i ->
            example("subj_${i}") has type being example("Example")
            example("subj_${i}") has example("count") being Quad.Literal(i)
            if (i < 10) {
                example("subj_${i}") has example("next") being example("subj_${i + 1}")
            }
            if (i > 0) {
                example("subj_${i}") has example("prev") being example("subj_${i - 1}")
            }
        }
    }

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
            FILTER(?c > 3)
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
            FILTER(?c <= 3)
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
            FILTER(?c > 2)
            FILTER(?c < 5)
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
            FILTER(?c > 2) .
            FILTER(?c < 5) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
            FILTER(?c > 2 && ?c < 5) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
            FILTER(?c < 3 || ?c > 5) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            # getting enough subject - count pairs to get a more complex join tree hierarchy
            # we have no filter going across a connected node; this solely checks disconnected nodes with filters
            ?s1 a :Example ; :count ?c1 .
            ?s2 a :Example ; :count ?c2 .
            ?s3 a :Example ; :count ?c3 .
            ?s4 a :Example ; :count ?c4 .
            FILTER(?c1 >= ?c2) .
            FILTER(?c2 >= ?c3) .
            FILTER(?c3 >= ?c4) .
            FILTER(?s1 != ?s2) .
            FILTER(?s2 != ?s3) .
            FILTER(?s3 != ?s4) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            # getting enough subject - count pairs to get a more complex join tree hierarchy
            # we have no filter going across a disconnected node; this solely checks connected nodes with filters
            ?s1 a :Example ; :count ?c1 ; :next ?s2 .
            ?s2 a :Example ; :count ?c2 ; :next ?s3 .
            ?s3 a :Example ; :count ?c3 .
            
            FILTER(?c1 != ?c2) .
            FILTER(?c2 != ?c3) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            # getting enough subject - count pairs to get a more complex join tree hierarchy
            # we have no filter going across a disconnected node; this solely checks connected nodes with filters
            ?s1 a :Example ; :count ?c1 ; :next ?s2 .
            ?s2 a :Example ; :count ?c2 ; :next ?s3 .
            ?s3 a :Example ; :count ?c3 .
            
            FILTER(?s1 != ?s2) .
            FILTER(?c1 != ?c2) .
            FILTER(?c2 != ?c3) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s (:next/:next)|(:prev/:prev) ?s_next .
            ?s_next :count ?c .
            FILTER(?c != 2) .
            FILTER(?s != ?s_next) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s (:next/:prev)|(:prev/:next) ?self .
            # should yield no results
            FILTER(?s != ?self) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s :next ?s_next .
            {
                ?s :count ?c1 .
            }
            # should only affect the single union segment
            FILTER(?c1 != 2) .
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
        } ORDER BY ASC(?c)
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
        } ORDER BY ASC(?c) LIMIT 2
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
        } ORDER BY DESC(?c)
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
        } ORDER BY DESC(?c) LIMIT 1
    """

    val timestamps = buildStore {
        val root = prefix("", "http://example.com/")
        val user = root("user")
        val user2 = root("user2")
        user has type being root("User")
        user has root("dob") being Quad.Literal("2000-01-01T01:00:00Z", XSD.dateTime)
        user2 has type being root("User")
        user2 has root("dob") being Quad.Literal("2020-01-01T01:00:00Z", XSD.dateTime)
    }

    using(timestamps) test """
        PREFIX : <http://example.com/>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        SELECT * WHERE {
            ?s a :User .
            ?s :dob ?dob .
            FILTER(?dob > "2010-01-01T00:00:00Z"^^xsd:dateTime) .
        }
    """

    using(timestamps) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            ?s a :User .
            ?s :dob ?dob .
        } ORDER BY ?dob
    """

    using(timestamps) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            ?s a :User .
            ?s :dob ?dob .
        } ORDER BY ?dob LIMIT 1
    """

    using(timestamps) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            ?s a :User .
            ?s :dob ?dob .
        } ORDER BY DESC(?dob)
    """

    using(timestamps) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            ?s a :User .
            ?s :dob ?dob .
        } ORDER BY DESC(?dob) LIMIT 2 OFFSET 1
    """

    val languages = buildStore {
        val root = prefix("", "http://example.com/")
        val user = root("user")
        user has type being root("User")
        user has root("name") being Quad.Literal("Name", "en")
        user has root("name") being Quad.Literal("Naam", "nl")
    }

    using(languages) test """
        PREFIX : <http://example.com/>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        SELECT * WHERE {
            ?s a :User .
            ?s :name ?name .
            FILTER LANGMATCHES(LANG(?name), "en") .
        }
    """

    val conditional = buildStore {
        val example = prefix("", "http://example.com/")
        val conditional = example("condition")
        val a = example("A")
        val b = example("B")
        a has conditional being Quad.Literal(false)
        b has conditional being Quad.Literal(true)
    }

    using(conditional) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            ?a :condition true
        }
    """

    using(conditional) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            ?a :condition ?condition .
            FILTER (?condition = true)
        }
    """

    val numbers = buildStore {
        val example = prefix("", "http://example.com/")
        example("a") has example("p") being 1
        example("a") has example("q") being 1
        example("a") has example("q") being 2

        example("b") has example("p") being 3.0
        example("b") has example("q") being 4.0
        example("b") has example("q") being 5.0
    }

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            ?x :p ?n
            FILTER NOT EXISTS {
                ?x :q ?m .
                FILTER(?n = ?m)
            }
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            ?x :p ?n
            FILTER NOT EXISTS {
                ?x :q ?m .
                FILTER(?n = ?m)
            } .
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            ?x :p ?n
            FILTER EXISTS {
                ?x :q ?m .
                FILTER(?n = ?m)
            }
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            ?x :p ?n
            FILTER EXISTS {
                ?x :q ?m .
                FILTER(?n = ?m)
            } .
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            ?x :p ?n
            FILTER NOT EXISTS {
                ?a1 ?a2 ?n .
                ?x :q ?m .
                FILTER(?n = ?m)
            }
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            ?x :p ?n
            FILTER NOT EXISTS {
                {
                    ?x :q ?m .
                } UNION {
                    ?y :q ?m .
                }
                FILTER(?n = ?m)
            }
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT ?n WHERE {
            ?n :p ?c1 .
            ?n :q ?c2 .
            FILTER(?c1 < ?c2 - 1.5)
        }
    """

    using(numbers) test """
        SELECT * {
            ?s ?p ?v
        }
        ORDER BY ?v DESC(?s) ?p
    """

    using(numbers) test """
        SELECT * {
            ?s ?p ?v
        }
        ORDER BY DESC(?v) DESC(?s) ?p
    """

    using(numbers) test """
        SELECT * {
            ?s ?p ?v
        }
        ORDER BY DESC(?v) DESC(?s) ?p LIMIT 3
    """

    using(numbers) test """
        SELECT * {
            ?s ?p ?v
        }
        ORDER BY ?v ?s DESC(?p)
    """

    using(numbers) test """
        SELECT * {
            ?s ?p ?v
        }
        ORDER BY ?v ?s DESC(?p) OFFSET 1
    """

    val filtered = buildStore {
        val example = prefix("", "http://example/")
        example("alice") has type being FOAF.Person
        example("alice") has FOAF("name") being example("name")
        example("name") has example("firstName") being Quad.Literal("Alice")
        example("name") has example("lastName") being Quad.Literal("LastName")
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
        val path1 = NamedTerm("http://example.org/path1")
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
        person has RDF.type being NamedTerm("http://example.org/person")
        person has NamedTerm("http://example.org/age") being 23
        person has NamedTerm("http://example.org/notes") being list(
            NamedTerm("http://example.org/first-note"),
            NamedTerm("http://example.org/second-note"),
            NamedTerm("http://example.org/third-note"),
            NamedTerm("http://example.org/fourth-note"),
            NamedTerm("http://example.org/another-note"),
            NamedTerm("http://example.org/last-note"),
        )
        person has NamedTerm("http://example.org/notes") being list(
            NamedTerm("http://example.org/even-more-notes")
        )
        person has NamedTerm("http://example.org/decoy") being list(
            NamedTerm("http://example.org/wrong-1"),
            NamedTerm("http://example.org/wrong-2"),
            NamedTerm("http://example.org/wrong-3"),
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
        val path = NamedTerm("http://example.org/path")
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
        val a = NamedTerm("http://example.org/a")
        val b = NamedTerm("http://example.org/b")
        val c = NamedTerm("http://example.org/c")
        val p = NamedTerm("http://example.org/p")
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
            ?a <http://example.org/p>* ?a
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

    using(unions) test """
        PREFIX : <http://www.example.org/>
        SELECT ?s WHERE {
            ?s (:p1|:p2)/(:p3|:p4) ?s
        }
    """

    val literals = buildStore {
        val a = NamedTerm("http://www.example.org/a")
        val b = NamedTerm("http://www.example.org/b")
        val c = NamedTerm("http://www.example.org/c")
        val d = NamedTerm("http://www.example.org/d")
        val p = NamedTerm("http://www.example.org/p")

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
            NamedTerm("https://www.example.org/street") being Quad.Literal("unknown")
            NamedTerm("https://www.example.org/number") being Quad.Literal((-1))
        }
        person has NamedTerm("notes") being list(
            NamedTerm("first-note"), NamedTerm("second-note")
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
        person has RDF.type being NamedTerm("person")
        person has NamedTerm("https://www.example.org/age") being 23
        person has NamedTerm("https://www.example.org/notes") being list(
            NamedTerm("first-note"),
            NamedTerm("second-note"),
            NamedTerm("third-note"),
            NamedTerm("fourth-note"),
            NamedTerm("another-note"),
            NamedTerm("last-note"),
        )
        person has NamedTerm("https://www.example.org/notes") being list(
            NamedTerm("even-more-notes")
        )
        person has NamedTerm("https://www.example.org/decoy") being list(
            NamedTerm("wrong-1"),
            NamedTerm("wrong-2"),
            NamedTerm("wrong-3"),
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
        NamedTerm("person1") has NamedTerm("https://www.example.org/domicile") being blank {
            NamedTerm("https://www.example.org/address") being blank {
                NamedTerm("https://www.example.org/street") being Quad.Literal("Person St.")
                NamedTerm("https://www.example.org/city") being blank {
                    NamedTerm("https://www.example.org/inhabitants") being 5000
                }
            }
        }
        NamedTerm("person2") has NamedTerm("https://www.example.org/domicile") being NamedTerm("house2")
        NamedTerm("house2") has NamedTerm("https://www.example.org/address") being NamedTerm("address2")
        NamedTerm("address2") has NamedTerm("https://www.example.org/street") being Quad.Literal("Person II St.")
        NamedTerm("address2") has NamedTerm("https://www.example.org/city") being blank {
            NamedTerm("https://www.example.org/inhabitants") being 7500
        }
        NamedTerm("incomplete") has NamedTerm("https://www.example.org/domicile") being blank {
            NamedTerm("https://www.example.org/address") being blank {
                NamedTerm("https://www.example.org/street") being NamedTerm("unknown")
                NamedTerm("https://www.example.org/city") being NamedTerm("unknown")
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

    using(aux2) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?a :p1 ?b .
            ?d :p2 ?c .
            FILTER (?a = :x)
        }        
    """

}
