package sparql.tests

import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import sparql.types.tests

object FOAF: Ontology {

    override val prefix = "foaf"
    override val base_uri = "http://xmlns.com/foaf/0.1/"

    val Person = Quad.NamedTerm("${base_uri}Person")
    val age = Quad.NamedTerm("${base_uri}age")
    val knows = Quad.NamedTerm("${base_uri}knows")
    val based_near = Quad.NamedTerm("${base_uri}based_near")

}

fun builtinTests() = tests {
    filter = DefaultTestFiltering

    val small = buildStore {
        val subj = local("s")
        val obj = local("o")
        val intermediate = local("i")
        val path1 = Quad.NamedTerm("http://example.org/path1")
        val path2 = Quad.NamedTerm("http://example.org/path2")
        (subj) (path1) (obj)
        (subj) (path2) (obj)
        (subj) (path1) (intermediate)
        (intermediate) (path1) (obj)
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
            ?s (<http://example.org/path1>|<http://example.org/path1>) ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s <http://example.org/path1> ?o .
            ?s <http://example.org/path1> ?o
        }
    """

    using(small) test """
        SELECT * {
            # should be identical to the test case above
            ?s <http://example.org/path1> ?o , ?o
        }
    """

    using(small) test """
        SELECT * {
            ?s (<http://example.org/path1>|!<http://example.org/path2>) ?o
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

    using(small) test """
        SELECT * {
            ?s <http://example.org/path1> ?o1
            OPTIONAL {
                ?o1 <http://example.org/path1> ?o2
            }
        }
    """

    using(small) test """
        SELECT * {
            ?s <http://example.org/path1> ?o1
            OPTIONAL {
                ?s <http://example.org/path2> ?o2
            }
        }
    """

    using(small) test """
        SELECT * {
            ?s <http://example.org/path1> ?o
            OPTIONAL {
                ?s <http://example.org/path2> ?o
            }
        }
    """

    using(small) test """
        SELECT * {
            ?s <http://example.org/path1> ?o
            OPTIONAL {
                ?s <http://example.org/path1> ?o
            }
        }
    """

    using(small) test """
        SELECT * {
            ?s <http://example.org/path1> ?o
            OPTIONAL {
                ?o <http://example.org/path1> ?o2
                FILTER(?o != ?o2)
            }
        }
    """

    using(small) test """
        SELECT * {
            OPTIONAL {
                ?s1 <http://example.org/path1> ?o1
            }
            OPTIONAL {
                ?s2 <http://example.org/path2> ?o2
            }
        }
    """

    using(small) test """
        SELECT * {
            OPTIONAL {
                ?s <http://example.org/path1> ?o1
            }
            OPTIONAL {
                ?s <http://example.org/path2> ?o2
            }
        }
    """

    val counts = buildStore {
        val example = prefix("", "http://example/")
        repeat(10) { i ->
            (example / "subj_${i}") a (example / "Example")
            (example / "subj_${i}") (example / "count") (Quad.Literal(i))
            if (i < 10) {
                (example / "subj_${i}") (example / "next") (example / "subj_${i + 1}")
            }
            if (i > 0) {
                (example / "subj_${i}") (example / "prev") (example / "subj_${i - 1}")
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
            ?s a :Example .
            OPTIONAL {
                FILTER(?c > 3)
                ?s :count ?c
            }
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
            ?s a :Example .
            OPTIONAL {
                ?s :count ?c .
                FILTER(?c <= 3)
            }
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
            BIND(?c * ?c AS ?c2)
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
            ?s a :Example .
            OPTIONAL {
                ?s :count ?c1 .
                FILTER(?c1 > 2)
            }
            OPTIONAL {
                ?s :count ?c2 .
                FILTER(?c2 < 5)
            }
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example .
            OPTIONAL {
                ?s :count ?c1 .
                FILTER(?c1 > 2)
            }
            OPTIONAL {
                ?s :count ?c2 .
                FILTER(?c2 < 5)
            }
            BIND(?c1 + ?c2 AS ?c3)
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
            BIND(?c1 + ?c2 + ?c3 + ?c4 AS ?c5)
        }
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            # getting enough subject - count pairs to get a more complex join tree hierarchy
            # we have no filter affecting a single optional block, instead, these are applied after
            # the optional joins have been evaluated
            ?s1 a :Example ; :count ?c1 .
            OPTIONAL {
                ?s2 a :Example ; :count ?c2 .
            }
            OPTIONAL {
                ?s3 a :Example ; :count ?c3 .
            }
            OPTIONAL {
                ?s4 a :Example ; :count ?c4 .
            }
            FILTER(?s1 != ?s2) .
            FILTER(?s2 != ?s3) .
            FILTER(?s3 != ?s4) .
            FILTER(?c1 >= ?c2) .
            FILTER(?c2 >= ?c3) .
            FILTER(?c3 >= ?c4) .
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
            ?s a :Example .
            OPTIONAL { ?s :count ?c }
        } ORDER BY ASC(?s) LIMIT 2
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
            ?s a :Example .
            OPTIONAL { ?s :count ?c }
        } ORDER BY DESC(?s)
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example ; :count ?c .
        } ORDER BY DESC(?c) LIMIT 1
    """

    using(counts) test """
        PREFIX : <http://example/>

        SELECT * WHERE {
            ?s a :Example .
            OPTIONAL { ?s :count ?c }
        } ORDER BY DESC(?s) LIMIT 1
    """

    val timestamps = buildStore {
        val root = prefix("", "http://example.com/")
        val user = root / "user"
        val user2 = root / "user2"
        user a root / "User"
        (user) (root /"dob") ("2000-01-01T01:00:00Z" `^^` XSD.dateTime)
        (user2) a root / "User"
        (user2) (root /"dob") ("2020-01-01T01:00:00Z" `^^` XSD.dateTime)
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
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        SELECT * WHERE {
            ?s a :User .
            OPTIONAL {
                ?s :dob ?dob .
                FILTER(?dob > "2010-01-01T00:00:00Z"^^xsd:dateTime) .
            }
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
            ?s :dob ?dob .
            OPTIONAL {
                ?s a :User .
            }
        } ORDER BY DESC(?dob)
    """

    using(timestamps) test """
        PREFIX : <http://example.com/>

        SELECT * WHERE {
            OPTIONAL {
                ?s a :User .
            }
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
        val user = root / "user"
        user a root / "User"
        (user) (root / "name") ("Name" % "en")
        (user) (root / "name") ("Naam" % "nl")
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

    using(languages) test """
        PREFIX : <http://example.com/>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        SELECT * WHERE {
            ?s a :User .
            OPTIONAL {
                ?s :name ?name .
                FILTER LANGMATCHES(LANG(?name), "en") .
            }
        }
    """

    val conditional = buildStore {
        val example = prefix("", "http://example.com/")
        val conditional = example / "condition"
        val a = example / "A"
        val b = example / "B"
        (a) (conditional) (Quad.Literal(false))
        (b) (conditional) (Quad.Literal(true))
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
        (example / "a") (example / "p") (1)
        (example / "a") (example / "q") (1)
        (example / "a") (example / "q") (2)

        (example / "b") (example / "p") (3.0)
        (example / "b") (example / "q") (4.0)
        (example / "b") (example / "q") (5.0)
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
            FILTER EXISTS {
                ?x :q ?m .
            }
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            OPTIONAL {
                ?x :p ?n
            }
            FILTER EXISTS {
                ?x :q ?m .
            }
        }
    """

    using(numbers) test """
        PREFIX : <http://example.com/>
        SELECT * WHERE {
            OPTIONAL {
                ?x :p ?n
            }
            FILTER NOT EXISTS {
                ?x :q ?m .
            }
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
        PREFIX : <http://example.com/>
        SELECT ?n WHERE {
            ?n :p ?c1 .
            OPTIONAL {
                ?n :q ?c2 .
            }
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
        (example / "alice") a FOAF.Person
        (example / "alice") (FOAF / "name") (example / "name")
        (example / "name") (example / "firstName") ("Alice" `^^` XSD.string)
        (example / "name") (example / "lastName") ("LastName" `^^` XSD.string)
        (example / "bob") a FOAF.Person
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

        SELECT ?person ?p ?o
        WHERE
        {
            ?person rdf:type foaf:Person .
            ?name ?p ?o .
            OPTIONAL {
                ?person foaf:name ?name .
            }
        }
    """

    using(filtered) test """
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person ?p ?o
        WHERE
        {
            ?person rdf:type foaf:Person .
            OPTIONAL {
                ?person foaf:name ?name .
            }
            ?name ?p ?o .
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
            OPTIONAL {
                ?person foaf:name ?name .
            }
            FILTER NOT EXISTS { ?name :firstName "Alice" }
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
            OPTIONAL {
                ?person foaf:name ?name .
            }
            FILTER EXISTS { ?name :firstName "Alice" }
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
                OPTIONAL {
                    ?name ?p ?o
                }
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

    using(filtered) test """
        PREFIX :        <http://example/>
        PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX  foaf:   <http://xmlns.com/foaf/0.1/>

        SELECT ?person
        WHERE
        {
            OPTIONAL {
                ?person rdf:type  foaf:Person .
            }
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
        val path1 = Quad.NamedTerm("http://example.org/path1")
        (subj) (path1) (obj)
        (subj) (path1) (intermediate)
        (intermediate) (path1) (obj)
    }

    using(extra) test """
        PREFIX : <http://example.org/>
        SELECT * {
            ?a :path1 ?o .
            ?a :path1 :o .
        }
    """

    using(extra) test """
        PREFIX : <http://example.org/>
        SELECT * {
            OPTIONAL {
                ?a :path1 ?o .
            }
            ?a :path1 :o .
        }
    """

    val medium = buildStore {
        val person = local("person1")
        val example = prefix("", "http://example.org/")
        (person) a (example / "person")
        (person) (example / "age") (23)
        (person) (example / "notes") list listOf(
            example / "first-note",
            example / "second-note",
            example / "third-note",
            example / "fourth-note",
            example / "another-note",
            example / "last-note",
        )
        (person) (example / "notes") list listOf(example / "even-more-notes")
        person (example / "decoy") list listOf(
            example / "wrong-1",
            example / "wrong-2",
            example / "wrong-3",
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
        val path = Quad.NamedTerm("http://example.org/path")
        (start) (path) (end)
        (start) (path) blank {
            (path) (end)
            (path) blank {
                (path) (end)
                (path) blank {
                    (path) (end)
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
        val a = Quad.NamedTerm("http://example.org/a")
        val b = Quad.NamedTerm("http://example.org/b")
        val c = Quad.NamedTerm("http://example.org/c")
        val p = Quad.NamedTerm("http://example.org/p")
        (a) (p) (b)
        (a) (p) (c)
        (b) (p) (a)
        (b) (p) (c)
        (c) (p) (a)
        (c) (p) (b)
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

        (a) (p1) (b)
        (b) (p4) (c)
        (a) (p2) (d)
        (d) (p3) (c)
        (a) (p1) (e)
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
        SELECT * WHERE {
            {
                :a :p1 ?b .
                BIND("1" as ?result1)
            } UNION {
                :a :p2 ?b .
                BIND("2" as ?result1)
            }
            {
                ?b :p3 ?s .
                BIND("1" as ?result2)
            } UNION {
                ?b :p4 ?s .
                BIND("2" as ?result2)
            }
        }
    """

    using(unions) test """
        PREFIX : <http://www.example.org/>
        SELECT ?s WHERE {
            {
                :a :p1 ?b .
            } UNION {
                ?b :p3 ?s .
            }
            {
                :a :p2 ?b .
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
        val a = Quad.NamedTerm("http://www.example.org/a")
        val b = Quad.NamedTerm("http://www.example.org/b")
        val c = Quad.NamedTerm("http://www.example.org/c")
        val d = Quad.NamedTerm("http://www.example.org/d")
        val p = Quad.NamedTerm("http://www.example.org/p")

        (a) (p) (11)
        (a) (p) (b)
        (b) (p) (12)
        (b) (p) (c)
        (c) (p) (13)
        (c) (p) (d)
        (d) (p) (14)
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
        val example = prefix("", "example")
        person a FOAF.Person
        (person) (FOAF.age) (23)
        (person) (FOAF.knows) (
            local("person2"), local("person3"), local("person4")
        )
        (person) (FOAF.based_near) blank {
            (example / "street") ("unknown" `^^` XSD.string)
            (example / "number") (-1)
        }
        (person) (Quad.NamedTerm("notes")) list listOf(
            Quad.NamedTerm("first-note"), Quad.NamedTerm("second-note")
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
        val example = prefix("", "https://example.org/")
        person a Quad.NamedTerm("person")
        (person) (example / "age") (23)
        (person) (example / "notes") list listOf(
            Quad.NamedTerm("first-note"),
            Quad.NamedTerm("second-note"),
            Quad.NamedTerm("third-note"),
            Quad.NamedTerm("fourth-note"),
            Quad.NamedTerm("another-note"),
            Quad.NamedTerm("last-note"),
        )
        (person) (example / "notes") list listOf(
            Quad.NamedTerm("even-more-notes")
        )
        (person) (example / "decoy") list listOf(
            Quad.NamedTerm("wrong-1"),
            Quad.NamedTerm("wrong-2"),
            Quad.NamedTerm("wrong-3"),
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
        (Quad.NamedTerm("person1")) (Quad.NamedTerm("https://www.example.org/domicile")) blank {
            (Quad.NamedTerm("https://www.example.org/address")) blank {
                (Quad.NamedTerm("https://www.example.org/street")) (Quad.Literal("Person St."))
                (Quad.NamedTerm("https://www.example.org/city")) blank {
                    (Quad.NamedTerm("https://www.example.org/inhabitants")) (5000)
                }
            }
        }
        (Quad.NamedTerm("person2")) (Quad.NamedTerm("https://www.example.org/domicile")) (Quad.NamedTerm("house2"))
        (Quad.NamedTerm("house2")) (Quad.NamedTerm("https://www.example.org/address")) (Quad.NamedTerm("address2"))
        (Quad.NamedTerm("address2")) (Quad.NamedTerm("https://www.example.org/street")) (Quad.Literal("Person II St."))
        (Quad.NamedTerm("address2")) (Quad.NamedTerm("https://www.example.org/city")) blank {
            Quad.NamedTerm("https://www.example.org/inhabitants") (7500)
        }
        (Quad.NamedTerm("incomplete")) (Quad.NamedTerm("https://www.example.org/domicile")) blank {
            (Quad.NamedTerm("https://www.example.org/address")) blank {
                (Quad.NamedTerm("https://www.example.org/street")) (Quad.NamedTerm("unknown"))
                (Quad.NamedTerm("https://www.example.org/city")) (Quad.NamedTerm("unknown"))
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

    using(addresses) test """
        PREFIX ex: <https://www.example.org/>
        SELECT * {
            ?person ex:domicile ?domicile .
            ?domicile ex:address ?address .
            ?address ex:street ?street .
            OPTIONAL {
                ?address ex:city [
                    ex:inhabitants ?count
                ]
            }
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

        (s0) (p2) (x)
        (x) (p1) (o)
        (s1) (p1) (o)
    }

    using(aux1) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?a :p1 ?b .
            ?d :p2 ?c .
            ?c :p1|:p2 :o
        }
    """

    using(aux1) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?c :p1|:p2 :o
            OPTIONAL {
                ?a :p1 ?b .
                ?d :p2 ?c .
            }
        }
    """

    using(aux1) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?c :p1|:p2 :o
            OPTIONAL {
                ?a :p1 ?b .
            }
            OPTIONAL {
                ?d :p2 ?c .
            }
        }
    """

    val aux2 = buildStore("http://example.org/") {
        val s0 = local("s0")
        val s1 = local("s1")
        val p1 = local("p1")
        val p2 = local("p2")
        val o = local("o")
        val x = local("x")

        (s0) (p2) (x)
        (s1) (p2) (o)
        (x) (p1) (o)
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

    val tightlyConnected = buildStore {
        val ex = prefix("ex", "http://example.org/")

        (ex / "s1") (ex / "p1") (ex / "s2")
        (ex / "s1") (ex / "p2") (ex / "s2")
        (ex / "s1") (ex / "p3") (ex / "s2")
        (ex / "s1") (ex / "p4") (ex / "s2")
        (ex / "s1") (ex / "p5") (ex / "s2")
        (ex / "s1") (ex / "p6") (ex / "s2")

        (ex / "s2") (ex / "p1") (ex / "s3")
        (ex / "s2") (ex / "p2") (ex / "s3")
        (ex / "s2") (ex / "p3") (ex / "s3")
        (ex / "s2") (ex / "p5") (ex / "s3")
        (ex / "s2") (ex / "p6") (ex / "s3")
    }

    using(tightlyConnected) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?s :p1 ?o .
            ?s :p2 ?o .
            ?s :p3 ?o .

            OPTIONAL {
                ?s :p4 ?o2 .
            }

            ?s :p5 ?o2 .
            ?s :p6 ?o2 .
        }
    """

    using(tightlyConnected) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            {
                ?s :p2 ?o2 .
                ?s :p3 ?o3 .
            } UNION {

                OPTIONAL {
                    ?s :p2 ?o2 .
                }

                ?s :p4 ?o4 .
                ?s :p5 ?o5 .
            }

            ?s :p1 ?o1 .

            OPTIONAL {
                ?o1 :p1 ?x .
                ?o1 :p2 ?y .
            }

            OPTIONAL {
                ?o2 ?p ?o .
            }
        }
    """

    using(tightlyConnected) test """
        PREFIX : <http://example.org/>
        SELECT * WHERE {
            ?s :p1 ?o1 .

            # even though these OPTIONAL blocks are
            # positioned relatively high up in the query body,
            # they can be evaluated after the two final TPs based
            # on the bindings they contain
            OPTIONAL {
                ?s :p2 ?o2 .
            }
            OPTIONAL {
                ?s :p3 ?o3 .
            }

            ?s :p4 ?o4 .
            ?s :p5 ?o5 .
        }
    """
}
